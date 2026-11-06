package com.ledgercore.transaction;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ledgercore.AbstractIntegrationTest;
import com.ledgercore.ApiClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class TransferConcurrencyIntegrationTest extends AbstractIntegrationTest {

    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private JdbcTemplate jdbcTemplate;

    private ApiClient api;

    @BeforeEach
    void setUpClient() {
        api = new ApiClient(mvc, objectMapper);
    }

    /** Runs every task on its own thread, released at the same instant, and waits for all of them. */
    private List<MvcResult> runConcurrently(List<Callable<MvcResult>> tasks) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(tasks.size());
        try {
            CountDownLatch ready = new CountDownLatch(tasks.size());
            CountDownLatch go = new CountDownLatch(1);
            List<Future<MvcResult>> futures = new ArrayList<>();
            for (Callable<MvcResult> task : tasks) {
                futures.add(pool.submit(() -> {
                    ready.countDown();
                    go.await();
                    return task.call();
                }));
            }
            assertThat(ready.await(30, TimeUnit.SECONDS)).isTrue();
            go.countDown();
            List<MvcResult> results = new ArrayList<>();
            for (Future<MvcResult> future : futures) {
                results.add(future.get(60, TimeUnit.SECONDS));   // a deadlock would show up here as a failure or a timeout
            }
            return results;
        } finally {
            pool.shutdownNow();
        }
    }

    private List<Integer> statusesOf(List<MvcResult> results) {
        return results.stream().map(result -> result.getResponse().getStatus()).toList();
    }

    private long count(String table) {
        return jdbcTemplate.queryForObject("select count(*) from " + table, Long.class);
    }

    /** Replays the wallet's ledger and checks that it adds up to the stored balance. */
    private void assertLedgerMatchesBalance(UUID walletId) {
        BigDecimal fromLedger = jdbcTemplate.queryForObject(
                "select coalesce(sum(case direction when 'CREDIT' then amount else -amount end), 0) "
                        + "from ledger_entries where wallet_id = ?", BigDecimal.class, walletId);
        BigDecimal stored = jdbcTemplate.queryForObject(
                "select balance from wallets where id = ?", BigDecimal.class, walletId);
        assertThat(fromLedger).isEqualByComparingTo(stored);
    }

    @Test
    void oppositeTransfersAtTheSameTimeNeitherDeadlockNorLoseMoney() throws Exception {
        String alice = api.tokenFor("alice@example.com");
        String bob = api.tokenFor("bob@example.com");
        UUID aliceWallet = api.createWallet(alice, "USD");
        UUID bobWallet = api.createWallet(bob, "USD");
        api.deposit(alice, aliceWallet, "seed-alice", "1000.00").andExpect(status().isCreated());
        api.deposit(bob, bobWallet, "seed-bob", "1000.00").andExpect(status().isCreated());

        List<Callable<MvcResult>> tasks = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            String key = "transfer-" + i;
            if (i % 4 == 0) {   // 5 transfers from Bob to Alice
                tasks.add(() -> api.transfer(bob, bobWallet, aliceWallet, key, "1.00").andReturn());
            } else {            // 15 transfers from Alice to Bob, in the opposite direction
                tasks.add(() -> api.transfer(alice, aliceWallet, bobWallet, key, "1.00").andReturn());
            }
        }

        List<MvcResult> results = runConcurrently(tasks);

        assertThat(statusesOf(results)).hasSize(20).containsOnly(201);
        assertThat(api.balanceOf(alice, aliceWallet)).isEqualTo("990.0000");   // 1000 - 15 + 5
        assertThat(api.balanceOf(bob, bobWallet)).isEqualTo("1010.0000");      // 1000 + 15 - 5
        assertLedgerMatchesBalance(aliceWallet);
        assertLedgerMatchesBalance(bobWallet);
    }

    @Test
    void tenParallelWithdrawalsCannotSpendMoreThanTheBalance() throws Exception {
        String alice = api.tokenFor("alice@example.com");
        UUID wallet = api.createWallet(alice, "USD");
        api.deposit(alice, wallet, "seed", "100.00").andExpect(status().isCreated());

        List<Callable<MvcResult>> tasks = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            String key = "withdraw-" + i;
            tasks.add(() -> api.withdraw(alice, wallet, key, "80.00").andReturn());
        }

        List<Integer> statuses = statusesOf(runConcurrently(tasks));

        assertThat(statuses.stream().filter(s -> s == 201).count()).isEqualTo(1);   // exactly one withdrawal wins
        assertThat(statuses.stream().filter(s -> s == 422).count()).isEqualTo(9);   // the rest are refused
        assertThat(api.balanceOf(alice, wallet)).isEqualTo("20.0000");
        assertLedgerMatchesBalance(wallet);
    }

    @Test
    void aRetryStormWithTheSameKeyCreditsExactlyOnce() throws Exception {
        String alice = api.tokenFor("alice@example.com");
        UUID wallet = api.createWallet(alice, "USD");

        List<Callable<MvcResult>> tasks = new ArrayList<>();
        for (int i = 0; i < 8; i++) {
            tasks.add(() -> api.deposit(alice, wallet, "one-key-for-all", "100.00").andReturn());
        }

        List<MvcResult> results = runConcurrently(tasks);

        assertThat(statusesOf(results)).hasSize(8).containsOnly(201);
        Set<String> references = new HashSet<>();
        for (MvcResult result : results) {
            references.add(objectMapper.readTree(result.getResponse().getContentAsString()).get("reference").asText());
        }
        assertThat(references).hasSize(1);   // every retry got the same receipt
        assertThat(api.balanceOf(alice, wallet)).isEqualTo("100.0000");
        assertThat(count("transactions")).isEqualTo(1);
        assertThat(count("ledger_entries")).isEqualTo(1);
    }
}
