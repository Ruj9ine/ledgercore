package com.ledgercore.admin;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ledgercore.AbstractIntegrationTest;
import com.ledgercore.ApiClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AdminWalletControllerIntegrationTest extends AbstractIntegrationTest {

    private static final String ADMIN_WALLETS = "/api/v1/admin/wallets";

    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private JdbcTemplate jdbcTemplate;

    private ApiClient api;

    @BeforeEach
    void setUpClient() {
        api = new ApiClient(mvc, objectMapper);
    }

    private String adminToken(String email) throws Exception {
        String token = api.tokenFor(email);
        promoteToAdmin(email);
        return token;
    }

    private UUID idOf(String email) {
        return jdbcTemplate.queryForObject("select id from users where email = ?", UUID.class, email);
    }

    private String statusOf(UUID walletId) {
        return jdbcTemplate.queryForObject("select status from wallets where id = ?", String.class, walletId);
    }

    private long auditCount(String action) {
        return jdbcTemplate.queryForObject("select count(*) from audit_log where action = ?", Long.class, action);
    }

    private long count(String table) {
        return jdbcTemplate.queryForObject("select count(*) from " + table, Long.class);
    }

    private String reason(String text) {
        return "{\"reason\":\"" + text + "\"}";
    }

    @Test
    void aNonAdminIsForbiddenOnEveryAdminWalletEndpoint() throws Exception {
        String alice = api.tokenFor("alice@example.com");
        UUID wallet = api.createWallet(alice, "USD");

        api.getAs(alice, ADMIN_WALLETS).andExpect(status().isForbidden());
        api.getAs(alice, ADMIN_WALLETS + "/" + wallet).andExpect(status().isForbidden());
        api.postAs(alice, ADMIN_WALLETS + "/" + wallet + "/freeze", reason("x")).andExpect(status().isForbidden());
        api.postAs(alice, ADMIN_WALLETS + "/" + wallet + "/unfreeze", reason("x")).andExpect(status().isForbidden());

        assertThat(statusOf(wallet)).isEqualTo("ACTIVE");
    }

    @Test
    void adminWalletEndpointsRequireAuthentication() throws Exception {
        mvc.perform(get(ADMIN_WALLETS)).andExpect(status().isUnauthorized());
        mvc.perform(post(ADMIN_WALLETS + "/" + UUID.randomUUID() + "/freeze")
                        .contentType(MediaType.APPLICATION_JSON).content(reason("x")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void theListShowsEveryonesWalletsNewestFirst() throws Exception {
        String admin = adminToken("admin@example.com");
        String alice = api.tokenFor("alice@example.com");
        String bob = api.tokenFor("bob@example.com");
        api.createWallet(alice, "USD");
        api.createWallet(bob, "UGX");

        api.getAs(admin, ADMIN_WALLETS)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.items[0].currency").value("UGX"))
                .andExpect(jsonPath("$.items[0].userId").value(idOf("bob@example.com").toString()))
                .andExpect(jsonPath("$.items[1].currency").value("USD"))
                .andExpect(jsonPath("$.items[1].status").value("ACTIVE"));
    }

    @Test
    void theListFiltersByCurrencyStatusAndOwner() throws Exception {
        String admin = adminToken("admin@example.com");
        String alice = api.tokenFor("alice@example.com");
        String bob = api.tokenFor("bob@example.com");
        api.createWallet(alice, "USD");
        api.createWallet(alice, "UGX");
        UUID bobWallet = api.createWallet(bob, "USD");
        jdbcTemplate.update("update wallets set status = 'FROZEN' where id = ?", bobWallet);
        String aliceId = idOf("alice@example.com").toString();

        api.getAs(admin, ADMIN_WALLETS, "currency", "USD").andExpect(jsonPath("$.totalElements").value(2));
        api.getAs(admin, ADMIN_WALLETS, "currency", "UGX").andExpect(jsonPath("$.totalElements").value(1));
        api.getAs(admin, ADMIN_WALLETS, "status", "FROZEN").andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.items[0].id").value(bobWallet.toString()));
        api.getAs(admin, ADMIN_WALLETS, "status", "ACTIVE").andExpect(jsonPath("$.totalElements").value(2));
        api.getAs(admin, ADMIN_WALLETS, "userId", aliceId).andExpect(jsonPath("$.totalElements").value(2));
        api.getAs(admin, ADMIN_WALLETS, "userId", aliceId, "currency", "UGX")
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void anAdminCanViewAWalletIncludingItsBalanceAndOwner() throws Exception {
        String admin = adminToken("admin@example.com");
        String alice = api.tokenFor("alice@example.com");
        UUID wallet = api.createWallet(alice, "USD");
        api.deposit(alice, wallet, "d1", "100.00").andExpect(status().isCreated());

        api.getAs(admin, ADMIN_WALLETS + "/" + wallet)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(wallet.toString()))
                .andExpect(jsonPath("$.userId").value(idOf("alice@example.com").toString()))
                .andExpect(jsonPath("$.currency").value("USD"))
                .andExpect(jsonPath("$.balance").value("100.0000"))
                .andExpect(jsonPath("$.status").value("ACTIVE"));
    }

    @Test
    void viewingAnUnknownWalletIsNotFound() throws Exception {
        String admin = adminToken("admin@example.com");

        api.getAs(admin, ADMIN_WALLETS + "/" + UUID.randomUUID()).andExpect(status().isNotFound());
    }

    @Test
    void freezingBlocksEveryKindOfMovementAndIsAudited() throws Exception {
        String admin = adminToken("admin@example.com");
        String alice = api.tokenFor("alice@example.com");
        String bob = api.tokenFor("bob@example.com");
        UUID aliceWallet = api.createWallet(alice, "USD");
        UUID bobWallet = api.createWallet(bob, "USD");
        api.deposit(alice, aliceWallet, "a1", "100.00").andExpect(status().isCreated());
        api.deposit(bob, bobWallet, "b1", "50.00").andExpect(status().isCreated());

        api.postAs(admin, ADMIN_WALLETS + "/" + aliceWallet + "/freeze", reason("  fraud review "))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("FROZEN"));

        api.deposit(alice, aliceWallet, "a2", "10.00").andExpect(status().isConflict());
        api.withdraw(alice, aliceWallet, "a3", "10.00").andExpect(status().isConflict());
        api.transfer(alice, aliceWallet, bobWallet, "a4", "10.00").andExpect(status().isConflict());
        api.transfer(bob, bobWallet, aliceWallet, "b2", "10.00").andExpect(status().isConflict());   // incoming is refused too

        assertThat(api.balanceOf(alice, aliceWallet)).isEqualTo("100.0000");   // the owner can still read it
        assertThat(api.balanceOf(bob, bobWallet)).isEqualTo("50.0000");        // the refused debit was rolled back
        assertThat(count("ledger_entries")).isEqualTo(2);
        Map<String, Object> row = jdbcTemplate.queryForMap(
                "select actor_user_id, target_type, target_id, reason, detail from audit_log where action = 'WALLET_FROZEN'");
        assertThat((UUID) row.get("actor_user_id")).isEqualTo(idOf("admin@example.com"));
        assertThat(row.get("target_type")).isEqualTo("WALLET");
        assertThat((UUID) row.get("target_id")).isEqualTo(aliceWallet);
        assertThat(row.get("reason")).isEqualTo("fraud review");
        assertThat(row.get("detail")).isEqualTo("was ACTIVE");
    }

    @Test
    void unfreezingRestoresMovementAndIsAudited() throws Exception {
        String admin = adminToken("admin@example.com");
        String alice = api.tokenFor("alice@example.com");
        UUID wallet = api.createWallet(alice, "USD");
        api.postAs(admin, ADMIN_WALLETS + "/" + wallet + "/freeze", reason("under review")).andExpect(status().isOk());
        api.deposit(alice, wallet, "d1", "10.00").andExpect(status().isConflict());

        api.postAs(admin, ADMIN_WALLETS + "/" + wallet + "/unfreeze", reason("review finished"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"));

        api.deposit(alice, wallet, "d2", "10.00").andExpect(status().isCreated());
        assertThat(auditCount("WALLET_FROZEN")).isEqualTo(1);
        assertThat(auditCount("WALLET_UNFROZEN")).isEqualTo(1);
    }

    @Test
    void freezingTwiceIsAConflictWithoutASecondAuditEntry() throws Exception {
        String admin = adminToken("admin@example.com");
        String alice = api.tokenFor("alice@example.com");
        UUID wallet = api.createWallet(alice, "USD");

        api.postAs(admin, ADMIN_WALLETS + "/" + wallet + "/freeze", reason("first")).andExpect(status().isOk());
        api.postAs(admin, ADMIN_WALLETS + "/" + wallet + "/freeze", reason("second")).andExpect(status().isConflict());

        assertThat(auditCount("WALLET_FROZEN")).isEqualTo(1);
    }

    @Test
    void unfreezingAnActiveWalletIsAConflictWithoutAnAuditEntry() throws Exception {
        String admin = adminToken("admin@example.com");
        String alice = api.tokenFor("alice@example.com");
        UUID wallet = api.createWallet(alice, "USD");

        api.postAs(admin, ADMIN_WALLETS + "/" + wallet + "/unfreeze", reason("not needed")).andExpect(status().isConflict());

        assertThat(auditCount("WALLET_UNFROZEN")).isZero();
    }

    @Test
    void invalidReasonsAreRejectedAndChangeNothing() throws Exception {
        String admin = adminToken("admin@example.com");
        String alice = api.tokenFor("alice@example.com");
        UUID wallet = api.createWallet(alice, "USD");
        String path = ADMIN_WALLETS + "/" + wallet + "/freeze";

        api.postAs(admin, path, "{}").andExpect(status().isBadRequest());
        api.postAs(admin, path, reason("   ")).andExpect(status().isBadRequest());
        api.postAs(admin, path, reason("x".repeat(501))).andExpect(status().isBadRequest());

        assertThat(statusOf(wallet)).isEqualTo("ACTIVE");
        assertThat(auditCount("WALLET_FROZEN")).isZero();
    }

    @Test
    void freezingAnUnknownWalletIsNotFoundAndWritesNoAudit() throws Exception {
        String admin = adminToken("admin@example.com");

        api.postAs(admin, ADMIN_WALLETS + "/" + UUID.randomUUID() + "/freeze", reason("who"))
                .andExpect(status().isNotFound());

        assertThat(auditCount("WALLET_FROZEN")).isZero();
    }

    @Test
    void anAdminCannotMoveMoneyInSomeoneElsesWallet() throws Exception {
        String admin = adminToken("admin@example.com");
        String alice = api.tokenFor("alice@example.com");
        UUID aliceWallet = api.createWallet(alice, "USD");

        api.deposit(admin, aliceWallet, "admin-deposit", "1000.00").andExpect(status().isNotFound());

        assertThat(api.balanceOf(alice, aliceWallet)).isEqualTo("0.0000");
        assertThat(count("ledger_entries")).isZero();
    }
}
