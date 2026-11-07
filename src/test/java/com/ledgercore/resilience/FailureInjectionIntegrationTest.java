package com.ledgercore.resilience;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ledgercore.AbstractIntegrationTest;
import com.ledgercore.ApiClient;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Makes the real database fail part-way through an operation, by installing a temporary trigger that raises an
 * error on a chosen insert. The application's own error handling plays no part: what is tested is whether any
 * damage is left behind, and whether a retry afterwards is safe. Expect ERROR lines with stack traces in the
 * output of these tests: they are the injected failures being logged.
 */
class FailureInjectionIntegrationTest extends AbstractIntegrationTest {

    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private JdbcTemplate jdbcTemplate;

    private ApiClient api;

    @BeforeEach
    void setUpClient() {
        api = new ApiClient(mvc, objectMapper);
    }

    /** The database is shared by every test class, so injected failures are always removed, even if a test fails. */
    @AfterEach
    void removeInjectedFailures() {
        jdbcTemplate.execute("DROP TRIGGER IF EXISTS trg_injected_failure ON ledger_entries");
        jdbcTemplate.execute("DROP TRIGGER IF EXISTS trg_injected_failure ON audit_log");
        jdbcTemplate.execute("DROP FUNCTION IF EXISTS test_injected_failure()");
    }

    /** Every insert into the table that matches the condition (written in terms of NEW) will fail. */
    private void injectFailure(String table, String condition) {
        jdbcTemplate.execute("""
                CREATE OR REPLACE FUNCTION test_injected_failure() RETURNS trigger AS $$
                BEGIN
                    RAISE EXCEPTION 'injected failure';
                END;
                $$ LANGUAGE plpgsql
                """);
        jdbcTemplate.execute("CREATE TRIGGER trg_injected_failure BEFORE INSERT ON " + table
                + " FOR EACH ROW WHEN (" + condition + ") EXECUTE FUNCTION test_injected_failure()");
    }

    private void removeFailure(String table) {
        jdbcTemplate.execute("DROP TRIGGER IF EXISTS trg_injected_failure ON " + table);
    }

    private long count(String table) {
        return jdbcTemplate.queryForObject("select count(*) from " + table, Long.class);
    }

    private long auditCount(String action) {
        return jdbcTemplate.queryForObject("select count(*) from audit_log where action = ?", Long.class, action);
    }

    private String walletStatus(UUID walletId) {
        return jdbcTemplate.queryForObject("select status from wallets where id = ?", String.class, walletId);
    }

    @ParameterizedTest
    @ValueSource(strings = {"CREDIT", "DEBIT"})
    void aTransferThatFailsPartWayLeavesNothingBehind(String failingSide) throws Exception {
        String alice = api.tokenFor("alice@example.com");
        String bob = api.tokenFor("bob@example.com");
        UUID aliceWallet = api.createWallet(alice, "USD");
        UUID bobWallet = api.createWallet(bob, "USD");
        api.deposit(alice, aliceWallet, "seed-a", "100.00").andExpect(status().isCreated());
        api.deposit(bob, bobWallet, "seed-b", "20.00").andExpect(status().isCreated());
        long transactionsBefore = count("transactions");
        long entriesBefore = count("ledger_entries");

        injectFailure("ledger_entries", "NEW.direction = '" + failingSide + "'");
        api.transfer(alice, aliceWallet, bobWallet, "t-1", "40.00").andExpect(status().isInternalServerError());

        assertThat(api.balanceOf(alice, aliceWallet)).isEqualTo("100.0000");
        assertThat(api.balanceOf(bob, bobWallet)).isEqualTo("20.0000");
        assertThat(count("transactions")).isEqualTo(transactionsBefore);
        assertThat(count("ledger_entries")).isEqualTo(entriesBefore);
        assertBooksBalance();
    }

    @Test
    void retryingAFailedTransferWithTheSameKeyWorksExactlyOnce() throws Exception {
        String alice = api.tokenFor("alice@example.com");
        String bob = api.tokenFor("bob@example.com");
        UUID aliceWallet = api.createWallet(alice, "USD");
        UUID bobWallet = api.createWallet(bob, "USD");
        api.deposit(alice, aliceWallet, "seed-a", "100.00").andExpect(status().isCreated());
        api.deposit(bob, bobWallet, "seed-b", "20.00").andExpect(status().isCreated());

        injectFailure("ledger_entries", "NEW.direction = 'CREDIT'");
        api.transfer(alice, aliceWallet, bobWallet, "retry-key", "40.00").andExpect(status().isInternalServerError());
        removeFailure("ledger_entries");

        // The failed attempt rolled back completely, so its key was never used up: the client's retry just works.
        String first = api.transfer(alice, aliceWallet, bobWallet, "retry-key", "40.00")
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        String again = api.transfer(alice, aliceWallet, bobWallet, "retry-key", "40.00")
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();

        assertThat(objectMapper.readTree(again).get("reference")).isEqualTo(objectMapper.readTree(first).get("reference"));
        assertThat(api.balanceOf(alice, aliceWallet)).isEqualTo("60.0000");
        assertThat(api.balanceOf(bob, bobWallet)).isEqualTo("60.0000");
        assertThat(count("transactions")).isEqualTo(3);     // two seed deposits and one transfer
        assertThat(count("ledger_entries")).isEqualTo(4);   // two seed entries and the transfer's pair
        assertBooksBalance();
    }

    @Test
    void aFailedDepositLeavesNothingBehindAndCanBeRetried() throws Exception {
        String alice = api.tokenFor("alice@example.com");
        UUID wallet = api.createWallet(alice, "USD");

        injectFailure("ledger_entries", "NEW.direction = 'CREDIT'");
        api.deposit(alice, wallet, "dep-key", "25.00").andExpect(status().isInternalServerError());

        assertThat(api.balanceOf(alice, wallet)).isEqualTo("0.0000");
        assertThat(count("transactions")).isZero();
        assertThat(count("ledger_entries")).isZero();

        removeFailure("ledger_entries");
        api.deposit(alice, wallet, "dep-key", "25.00").andExpect(status().isCreated());

        assertThat(api.balanceOf(alice, wallet)).isEqualTo("25.0000");
        assertBooksBalance();
    }

    @Test
    void aFailedWithdrawalLeavesTheBalanceAloneAndCanBeRetried() throws Exception {
        String alice = api.tokenFor("alice@example.com");
        UUID wallet = api.createWallet(alice, "USD");
        api.deposit(alice, wallet, "seed", "100.00").andExpect(status().isCreated());

        injectFailure("ledger_entries", "NEW.direction = 'DEBIT'");
        api.withdraw(alice, wallet, "wd-key", "30.00").andExpect(status().isInternalServerError());

        assertThat(api.balanceOf(alice, wallet)).isEqualTo("100.0000");
        assertThat(count("ledger_entries")).isEqualTo(1);

        removeFailure("ledger_entries");
        api.withdraw(alice, wallet, "wd-key", "30.00").andExpect(status().isCreated());

        assertThat(api.balanceOf(alice, wallet)).isEqualTo("70.0000");
        assertBooksBalance();
    }

    @Test
    void anAdminActionAndItsAuditEntrySucceedOrFailTogether() throws Exception {
        String admin = api.tokenFor("admin@example.com");
        promoteToAdmin("admin@example.com");
        String alice = api.tokenFor("alice@example.com");
        UUID wallet = api.createWallet(alice, "USD");

        injectFailure("audit_log", "NEW.action = 'WALLET_FROZEN'");
        api.postAs(admin, "/api/v1/admin/wallets/" + wallet + "/freeze", "{\"reason\":\"fraud review\"}")
                .andExpect(status().isInternalServerError());
        removeFailure("audit_log");

        // If the audit entry cannot be written, the freeze must not have happened either.
        assertThat(walletStatus(wallet)).isEqualTo("ACTIVE");
        assertThat(auditCount("WALLET_FROZEN")).isZero();
        api.deposit(alice, wallet, "d1", "10.00").andExpect(status().isCreated());   // and the wallet still works
    }

    @Test
    void aFailureToAuditARejectionNeverChangesTheAnswerTheUserGets() throws Exception {
        String alice = api.tokenFor("alice@example.com");
        UUID wallet = api.createWallet(alice, "USD");
        api.deposit(alice, wallet, "seed", "100.00").andExpect(status().isCreated());

        injectFailure("audit_log", "NEW.action = 'REQUEST_REJECTED'");
        api.withdraw(alice, wallet, "w1", "999.00").andExpect(status().isUnprocessableEntity());
        removeFailure("audit_log");

        assertThat(auditCount("REQUEST_REJECTED")).isZero();   // the audit entry could not be written; the 422 still went out
        assertThat(api.balanceOf(alice, wallet)).isEqualTo("100.0000");
    }
}