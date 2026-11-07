package com.ledgercore.reconciliation;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ledgercore.AbstractIntegrationTest;
import com.ledgercore.ApiClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ReconciliationIntegrationTest extends AbstractIntegrationTest {

    private static final String PATH = "/api/v1/admin/reconciliation";
    private static final String BALANCE = "wallet-balance-matches-ledger";
    private static final String CHAIN = "ledger-balance-chain";
    private static final String SHAPE = "transaction-ledger-shape";
    private static final String SUPPLY = "money-supply-per-currency";

    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private JdbcTemplate jdbcTemplate;

    private ApiClient api;
    private String admin;

    @BeforeEach
    void setUp() throws Exception {
        api = new ApiClient(mvc, objectMapper);
        admin = api.tokenFor("admin@example.com");
        promoteToAdmin("admin@example.com");
    }

    private JsonNode report() throws Exception {
        String body = api.getAs(admin, PATH).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body);
    }

    private JsonNode check(JsonNode report, String name) {
        for (JsonNode check : report.get("checks")) {
            if (check.get("name").asText().equals(name)) {
                return check;
            }
        }
        throw new AssertionError("The report has no check named " + name);
    }

    private void assertPassed(JsonNode report, String... names) {
        for (String name : names) {
            assertThat(check(report, name).get("passed").asBoolean()).as("check %s should pass", name).isTrue();
        }
    }

    private void assertFailed(JsonNode report, String... names) {
        for (String name : names) {
            assertThat(check(report, name).get("passed").asBoolean()).as("check %s should fail", name).isFalse();
        }
    }

    /** Alice and Bob each hold a USD wallet. Alice: +100, -20, -30 (to Bob), +5.50 (from Bob). Bob: +50, +30, -5.50. */
    private record Wallets(UUID alice, UUID bob) {
    }

    private Wallets busyBooks() throws Exception {
        String alice = api.tokenFor("alice@example.com");
        String bob = api.tokenFor("bob@example.com");
        UUID aliceWallet = api.createWallet(alice, "USD");
        UUID bobWallet = api.createWallet(bob, "USD");
        api.deposit(alice, aliceWallet, "a1", "100.00").andExpect(status().isCreated());
        api.deposit(bob, bobWallet, "b1", "50.00").andExpect(status().isCreated());
        api.withdraw(alice, aliceWallet, "a2", "20.00").andExpect(status().isCreated());
        api.transfer(alice, aliceWallet, bobWallet, "a3", "30.00").andExpect(status().isCreated());
        api.transfer(bob, bobWallet, aliceWallet, "b2", "5.50").andExpect(status().isCreated());
        return new Wallets(aliceWallet, bobWallet);
    }

    /** Switches the ledger's immutability trigger off for the tamper, and always switches it back on. */
    private void withLedgerTriggerDisabled(Runnable tamper) {
        jdbcTemplate.execute("ALTER TABLE ledger_entries DISABLE TRIGGER trg_ledger_entries_immutable");
        try {
            tamper.run();
        } finally {
            jdbcTemplate.execute("ALTER TABLE ledger_entries ENABLE TRIGGER trg_ledger_entries_immutable");
        }
    }

    @Test
    void anEmptySystemIsBalanced() throws Exception {
        JsonNode report = report();

        assertThat(report.get("balanced").asBoolean()).isTrue();
        assertThat(report.get("checkedAt").asText()).isNotEmpty();
        assertThat(report.get("checks")).hasSize(4);
    }

    @Test
    void afterNormalActivityEveryCheckPasses() throws Exception {
        busyBooks();

        JsonNode report = report();

        assertThat(report.get("balanced").asBoolean()).isTrue();
        List<String> names = new ArrayList<>();
        for (JsonNode check : report.get("checks")) {
            names.add(check.get("name").asText());
            assertThat(check.get("passed").asBoolean()).isTrue();
            assertThat(check.get("problemCount").asLong()).isZero();
            assertThat(check.get("samples")).isEmpty();
            assertThat(check.get("description").asText()).isNotEmpty();
        }
        assertThat(names).containsExactly(BALANCE, CHAIN, SHAPE, SUPPLY);
    }

    @Test
    void aTamperedWalletBalanceIsDetected() throws Exception {
        Wallets wallets = busyBooks();
        jdbcTemplate.update("update wallets set balance = balance + 1 where id = ?", wallets.alice());

        JsonNode report = report();

        assertThat(report.get("balanced").asBoolean()).isFalse();
        assertFailed(report, BALANCE, SUPPLY);
        assertPassed(report, CHAIN, SHAPE);
        JsonNode balanceCheck = check(report, BALANCE);
        assertThat(balanceCheck.get("problemCount").asLong()).isEqualTo(1);
        assertThat(balanceCheck.get("samples").get(0).asText()).contains(wallets.alice().toString());
    }

    @Test
    void aForgedLedgerEntryIsDetected() throws Exception {
        busyBooks();
        // Inserting is allowed by the immutability trigger (only updates and deletes are blocked), so this is a real attack.
        jdbcTemplate.update("insert into ledger_entries (id, transaction_id, wallet_id, direction, amount, balance_after, created_at) "
                + "select gen_random_uuid(), transaction_id, wallet_id, 'CREDIT', 5, 5, now() from ledger_entries limit 1");

        JsonNode report = report();

        assertThat(report.get("balanced").asBoolean()).isFalse();
        assertFailed(report, BALANCE, CHAIN, SHAPE);
        assertPassed(report, SUPPLY);   // no wallet or transaction changed, only the ledger
    }

    @Test
    void aDeletedLedgerEntryIsDetected() throws Exception {
        busyBooks();
        withLedgerTriggerDisabled(() ->
                jdbcTemplate.update("delete from ledger_entries where id = (select id from ledger_entries limit 1)"));

        JsonNode report = report();

        assertThat(report.get("balanced").asBoolean()).isFalse();
        assertFailed(report, BALANCE, SHAPE);
        assertPassed(report, SUPPLY);
    }

    @Test
    void aTamperedBalanceAfterIsDetectedByTheChainCheckAlone() throws Exception {
        busyBooks();
        withLedgerTriggerDisabled(() -> jdbcTemplate.update(
                "update ledger_entries set balance_after = balance_after + 1 "
                        + "where id = (select id from ledger_entries order by created_at limit 1)"));

        JsonNode report = report();

        assertThat(report.get("balanced").asBoolean()).isFalse();
        assertFailed(report, CHAIN);
        assertThat(check(report, CHAIN).get("problemCount").asLong()).isEqualTo(1);
        assertPassed(report, BALANCE, SHAPE, SUPPLY);
    }

    @Test
    void aNonAdminIsForbidden() throws Exception {
        String alice = api.tokenFor("alice@example.com");

        api.getAs(alice, PATH).andExpect(status().isForbidden());
    }

    @Test
    void reconciliationRequiresAuthentication() throws Exception {
        mvc.perform(get(PATH)).andExpect(status().isUnauthorized());
    }
}
