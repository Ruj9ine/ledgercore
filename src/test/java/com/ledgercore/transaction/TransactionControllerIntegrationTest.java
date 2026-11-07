package com.ledgercore.transaction;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ledgercore.AbstractIntegrationTest;
import com.ledgercore.ApiClient;
import com.ledgercore.wallet.Wallet;
import com.ledgercore.wallet.WalletRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;


class TransactionControllerIntegrationTest extends AbstractIntegrationTest {

    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private WalletRepository walletRepository;

   private ApiClient api;

   @BeforeEach
   void setUpClient() {
       api = new ApiClient(mvc, objectMapper);
   }

    private long count(String table) {
        return jdbcTemplate.queryForObject("select count(*) from " + table, Long.class);
    }

    @Test
    void depositIncreasesTheBalanceAndReturnsAReceipt() throws Exception {
        String token = api.tokenFor("alice@example.com");
        UUID walletId = api.createWallet(token, "UGX");

        api.deposit(token, walletId, "dep-1", "5000")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.reference").value(org.hamcrest.Matchers.matchesPattern("TXN-[2-9A-HJKMNP-Z]{10}")))
                .andExpect(jsonPath("$.type").value("DEPOSIT"))
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.amount").value("5000.0000"))
                .andExpect(jsonPath("$.balanceAfter").value("5000.0000"));

        assertThat(api.balanceOf(token, walletId)).isEqualTo("5000.0000");
    }

    @Test
    void withdrawalDecreasesTheBalance() throws Exception {
        String token = api.tokenFor("alice@example.com");
        UUID walletId = api.createWallet(token, "UGX");
        api.deposit(token, walletId, "dep-1", "5000").andExpect(status().isCreated());

        api.withdraw(token, walletId, "wd-1", "1500")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.type").value("WITHDRAWAL"))
                .andExpect(jsonPath("$.balanceAfter").value("3500.0000"));

        assertThat(api.balanceOf(token, walletId)).isEqualTo("3500.0000");
    }

    @Test
    void overdraftIsRefusedWithoutChangingTheBalanceOrTheLedger() throws Exception {
        String token = api.tokenFor("alice@example.com");
        UUID walletId = api.createWallet(token, "USD");
        api.deposit(token, walletId, "dep-1", "100.00").andExpect(status().isCreated());

        api.withdraw(token, walletId, "wd-1", "100.01").andExpect(status().isUnprocessableEntity());

        assertThat(api.balanceOf(token, walletId)).isEqualTo("100.0000");
        assertThat(count("ledger_entries")).isEqualTo(1);
        assertThat(count("transactions")).isEqualTo(1);
    }

    @Test
    void repeatingARequestWithTheSameKeyDoesNotCreditTwice() throws Exception {
        String token = api.tokenFor("alice@example.com");
        UUID walletId = api.createWallet(token, "USD");

        String first = api.deposit(token, walletId, "retry-me", "100.00")
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        String second = api.deposit(token, walletId, "retry-me", "100.00")
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();

        assertThat(objectMapper.readTree(second).get("reference")).isEqualTo(objectMapper.readTree(first).get("reference"));
        assertThat(api.balanceOf(token, walletId)).isEqualTo("100.0000");
        assertThat(count("transactions")).isEqualTo(1);
        assertThat(count("ledger_entries")).isEqualTo(1);
    }

    @Test
    void reusingAKeyForADifferentAmountIsRejected() throws Exception {
        String token = api.tokenFor("alice@example.com");
        UUID walletId = api.createWallet(token, "USD");
        api.deposit(token, walletId, "same-key", "100.00").andExpect(status().isCreated());

        api.deposit(token, walletId, "same-key", "200.00").andExpect(status().isConflict());

        assertThat(api.balanceOf(token, walletId)).isEqualTo("100.0000");
    }

    @Test
    void theIdempotencyKeyHeaderIsRequired() throws Exception {
        String token = api.tokenFor("alice@example.com");
        UUID walletId = api.createWallet(token, "USD");

        api.deposit(token, walletId, null, "100.00").andExpect(status().isBadRequest());

        assertThat(count("ledger_entries")).isZero();
    }

    @ParameterizedTest
    @CsvSource({
            "UGX, 0",
            "UGX, -5",
            "UGX, 10.5",
            "USD, 10.005",
            "USD, 1000000000.01"
    })
    void invalidAmountsAreRejected(String currency, String amount) throws Exception {
        String token = api.tokenFor("alice@example.com");
        UUID walletId = api.createWallet(token, currency);

        api.deposit(token, walletId, "bad-amount", amount).andExpect(status().isBadRequest());

        assertThat(count("ledger_entries")).isZero();
    }

    @Test
    void anotherUsersWalletIsNotFound() throws Exception {
        String alice = api.tokenFor("alice@example.com");
        String bob = api.tokenFor("bob@example.com");
        UUID aliceWalletId = api.createWallet(alice, "USD");

        api.deposit(bob, aliceWalletId, "intruder", "100.00").andExpect(status().isNotFound());

        assertThat(api.balanceOf(alice, aliceWalletId)).isEqualTo("0.0000");
    }

    @Test
    void aFrozenWalletRejectsMovements() throws Exception {
        String token = api.tokenFor("alice@example.com");
        UUID walletId = api.createWallet(token, "USD");
        Wallet wallet = walletRepository.findById(walletId).orElseThrow();
        wallet.freeze();
        walletRepository.saveAndFlush(wallet);

        api.deposit(token, walletId, "frozen-1", "100.00").andExpect(status().isConflict());
    }

    @Test
    void movementEndpointsRequireAuthentication() throws Exception {
        mvc.perform(post("/api/v1/wallets/" + UUID.randomUUID() + "/deposits")
                        .header("Idempotency-Key", "no-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\":\"100\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void theLedgerAlwaysAddsUpToTheWalletBalance() throws Exception {
        String token = api.tokenFor("alice@example.com");
        UUID walletId = api.createWallet(token, "USD");
        api.deposit(token, walletId, "a", "100.00").andExpect(status().isCreated());
        api.deposit(token, walletId, "b", "50.25").andExpect(status().isCreated());
        api.withdraw(token, walletId, "c", "30.10").andExpect(status().isCreated());
        api.deposit(token, walletId, "d", "0.01").andExpect(status().isCreated());
        api.withdraw(token, walletId, "e", "20.00").andExpect(status().isCreated());

        List<Map<String, Object>> entries = jdbcTemplate.queryForList(
                "select direction, amount, balance_after from ledger_entries where wallet_id = ? order by created_at",
                walletId);
        BigDecimal running = BigDecimal.ZERO;
        for (Map<String, Object> entry : entries) {
            BigDecimal amount = (BigDecimal) entry.get("amount");
            running = "CREDIT".equals(entry.get("direction")) ? running.add(amount) : running.subtract(amount);
            // Every entry records the true balance at that moment, so the history can be audited step by step.
            assertThat((BigDecimal) entry.get("balance_after")).isEqualByComparingTo(running);
        }
        assertThat(entries).hasSize(5);
        assertThat(new BigDecimal(api.balanceOf(token, walletId))).isEqualByComparingTo("100.16");
        assertThat(running).isEqualByComparingTo("100.16");
    }

    @Test
    void ledgerRowsCannotBeUpdatedOrDeleted() throws Exception {
        String token = api.tokenFor("alice@example.com");
        UUID walletId = api.createWallet(token, "USD");
        api.deposit(token, walletId, "dep-1", "100.00").andExpect(status().isCreated());

        assertThatThrownBy(() -> jdbcTemplate.update("UPDATE ledger_entries SET amount = 1"))
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("append-only");
        assertThatThrownBy(() -> jdbcTemplate.update("DELETE FROM ledger_entries"))
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("append-only");

        assertThat(count("ledger_entries")).isEqualTo(1);
    }
}