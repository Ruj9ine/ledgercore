package com.ledgercore.resilience;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ledgercore.AbstractIntegrationTest;
import com.ledgercore.ApiClient;
import com.ledgercore.Concurrently;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class MixedWorkloadStressIntegrationTest extends AbstractIntegrationTest {

    private static final int OPERATIONS = 150;
    private static final int THREADS = 8;
    private static final long SEED = 20261107L;   // fixed, so a failing run can be repeated
    private static final BigDecimal SEED_BALANCE = new BigDecimal("1000.00");

    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private JdbcTemplate jdbcTemplate;

    private ApiClient api;

    private enum Kind { DEPOSIT, WITHDRAWAL, TRANSFER }

    private record Operation(Kind kind, int walletIndex, int otherIndex, String amount, String key) {
    }

    private record Outcome(Operation operation, int status, String reference) {
    }

    @BeforeEach
    void setUpClient() {
        api = new ApiClient(mvc, objectMapper);
    }

    private static String amount(Random random, int maxWhole) {
        return String.format(Locale.ROOT, "%d.%02d", 1 + random.nextInt(maxWhole), random.nextInt(100));
    }

    /** About 30% deposits, 30% withdrawals and 40% transfers, with every fifth request repeated, in random order. */
    private List<Operation> schedule(int users, Random random) {
        List<Operation> operations = new ArrayList<>();
        for (int i = 0; i < OPERATIONS; i++) {
            int from = random.nextInt(users);
            int roll = random.nextInt(10);
            String key = "op-" + i;
            if (roll < 3) {
                operations.add(new Operation(Kind.DEPOSIT, from, from, amount(random, 50), key));
            } else if (roll < 6) {
                operations.add(new Operation(Kind.WITHDRAWAL, from, from, amount(random, 400), key));
            } else {
                int other = (from + 1 + random.nextInt(users - 1)) % users;
                operations.add(new Operation(Kind.TRANSFER, from, other, amount(random, 400), key));
            }
        }
        List<Operation> schedule = new ArrayList<>(operations);
        for (int i = 0; i < operations.size(); i += 5) {
            schedule.add(operations.get(i));   // the same key and body again: a client retrying
        }
        Collections.shuffle(schedule, random);
        return schedule;
    }

    private Outcome execute(Operation operation, List<String> tokens, List<UUID> wallets) throws Exception {
        String token = tokens.get(operation.walletIndex());
        UUID wallet = wallets.get(operation.walletIndex());
        MvcResult result = switch (operation.kind()) {
            case DEPOSIT -> api.deposit(token, wallet, operation.key(), operation.amount()).andReturn();
            case WITHDRAWAL -> api.withdraw(token, wallet, operation.key(), operation.amount()).andReturn();
            case TRANSFER -> api.transfer(token, wallet, wallets.get(operation.otherIndex()),
                    operation.key(), operation.amount()).andReturn();
        };
        int status = result.getResponse().getStatus();
        String reference = status == 201
                ? objectMapper.readTree(result.getResponse().getContentAsString()).get("reference").asText()
                : null;
        return new Outcome(operation, status, reference);
    }

    private long count(String sql) {
        return jdbcTemplate.queryForObject(sql, Long.class);
    }

    @ParameterizedTest
    @ValueSource(ints = {2, 6})   // 2 wallets is maximum contention; 6 spreads the load around
    void aMixedConcurrentWorkloadKeepsTheBooksExactlyBalanced(int users) throws Exception {
        List<String> tokens = new ArrayList<>();
        List<UUID> wallets = new ArrayList<>();
        for (int i = 0; i < users; i++) {
            String token = api.tokenFor("user" + i + "@example.com");
            UUID wallet = api.createWallet(token, "USD");
            api.deposit(token, wallet, "seed-" + i, SEED_BALANCE.toPlainString()).andExpect(status().isCreated());
            tokens.add(token);
            wallets.add(wallet);
        }
        List<Callable<Outcome>> tasks = new ArrayList<>();
        for (Operation operation : schedule(users, new Random(SEED + users))) {
            tasks.add(() -> execute(operation, tokens, wallets));
        }

        List<Outcome> outcomes = Concurrently.run(tasks, THREADS);

        // 1. Every request was either accepted or refused for a business reason. Nothing crashed or deadlocked.
        List<String> unexpected = outcomes.stream()
                .filter(o -> o.status() != 201 && o.status() != 422)
                .map(o -> o.operation() + " -> " + o.status())
                .toList();
        assertThat(unexpected).as("requests that were neither accepted nor refused for insufficient funds").isEmpty();
        long accepted = outcomes.stream().filter(o -> o.status() == 201).count();
        long rejected = outcomes.stream().filter(o -> o.status() == 422).count();
        assertThat(accepted).as("the workload must include accepted requests").isPositive();
        assertThat(rejected).as("the workload must include requests refused for insufficient funds").isPositive();

        // 2. Rebuild what every wallet should hold from the responses alone. A replayed receipt is counted once.
        Map<UUID, BigDecimal> expected = new HashMap<>();
        wallets.forEach(wallet -> expected.put(wallet, SEED_BALANCE));
        Map<String, Set<String>> receiptsByKey = new HashMap<>();
        Set<String> counted = new HashSet<>();
        BigDecimal deposited = BigDecimal.ZERO;
        BigDecimal withdrawn = BigDecimal.ZERO;
        long deposits = 0;
        long withdrawals = 0;
        long transfers = 0;
        for (Outcome outcome : outcomes) {
            if (outcome.status() != 201) {
                continue;
            }
            Operation operation = outcome.operation();
            receiptsByKey.computeIfAbsent(operation.key(), key -> new HashSet<>()).add(outcome.reference());
            if (!counted.add(outcome.reference())) {
                continue;   // a replay of a request that has already been counted
            }
            BigDecimal amount = new BigDecimal(operation.amount());
            UUID from = wallets.get(operation.walletIndex());
            switch (operation.kind()) {
                case DEPOSIT -> {
                    expected.merge(from, amount, BigDecimal::add);
                    deposited = deposited.add(amount);
                    deposits++;
                }
                case WITHDRAWAL -> {
                    expected.merge(from, amount.negate(), BigDecimal::add);
                    withdrawn = withdrawn.add(amount);
                    withdrawals++;
                }
                case TRANSFER -> {
                    expected.merge(from, amount.negate(), BigDecimal::add);
                    expected.merge(wallets.get(operation.otherIndex()), amount, BigDecimal::add);
                    transfers++;
                }
            }
        }

        // 3. However many times a key was sent, it produced at most one receipt: the idempotency promise under a race.
        receiptsByKey.forEach((key, receipts) ->
                assertThat(receipts).as("receipts returned for key %s", key).hasSize(1));

        // 4. Every wallet holds exactly what the model says, and none went negative.
        for (int i = 0; i < users; i++) {
            BigDecimal actual = new BigDecimal(api.balanceOf(tokens.get(i), wallets.get(i)));
            assertThat(actual).as("balance of wallet %d", i).isEqualByComparingTo(expected.get(wallets.get(i)));
        }
        assertThat(count("select count(*) from wallets where balance < 0")).isZero();

        // 5. Money was neither created nor destroyed by transfers: the total is seeds plus deposits minus withdrawals.
        BigDecimal totalHeld = jdbcTemplate.queryForObject("select sum(balance) from wallets", BigDecimal.class);
        BigDecimal expectedTotal = SEED_BALANCE.multiply(BigDecimal.valueOf(users)).add(deposited).subtract(withdrawn);
        assertThat(totalHeld).isEqualByComparingTo(expectedTotal);

        // 6. The tables contain exactly the rows the accepted requests should have produced, and no others.
        assertThat(count("select count(*) from transactions")).isEqualTo(users + deposits + withdrawals + transfers);
        assertThat(count("select count(*) from ledger_entries")).isEqualTo(users + deposits + withdrawals + 2 * transfers);

        // 7. Every refused request left exactly one audit entry, even with many running at once.
        assertThat(count("select count(*) from audit_log where action = 'REQUEST_REJECTED'")).isEqualTo(rejected);

        // 8. And the application's own books agree with all of the above.
        assertBooksBalance();
    }
}
