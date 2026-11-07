package com.ledgercore.transaction;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ledgercore.AbstractIntegrationTest;
import com.ledgercore.ApiClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class MoneyRejectionAuditIntegrationTest extends AbstractIntegrationTest {

    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private JdbcTemplate jdbcTemplate;

    private ApiClient api;

    @BeforeEach
    void setUpClient() {
        api = new ApiClient(mvc, objectMapper);
    }

    private List<Map<String, Object>> rejections() {
        return jdbcTemplate.queryForList(
                "select actor_user_id, target_type, target_id, reason, detail from audit_log "
                        + "where action = 'REQUEST_REJECTED' order by occurred_at");
    }

    private UUID idOf(String email) {
        return jdbcTemplate.queryForObject("select id from users where email = ?", UUID.class, email);
    }

    @Test
    void aRefusedWithdrawalIsAuditedWithoutTheAmount() throws Exception {
        String alice = api.tokenFor("alice@example.com");
        UUID wallet = api.createWallet(alice, "USD");
        api.deposit(alice, wallet, "d1", "100.00").andExpect(status().isCreated());

        api.withdraw(alice, wallet, "w1", "999.00").andExpect(status().isUnprocessableEntity());

        List<Map<String, Object>> rows = rejections();
        assertThat(rows).hasSize(1);
        Map<String, Object> row = rows.get(0);
        assertThat((UUID) row.get("actor_user_id")).isEqualTo(idOf("alice@example.com"));
        assertThat(row.get("target_type")).isEqualTo("WALLET");
        assertThat((UUID) row.get("target_id")).isEqualTo(wallet);
        assertThat(row.get("reason")).isNull();
        assertThat(row.get("detail")).isEqualTo("withdrawals rejected with status 422");
        assertThat((String) row.get("detail")).doesNotContain("999");
    }

    @Test
    void successfulRequestsAreNotAudited() throws Exception {
        String alice = api.tokenFor("alice@example.com");
        String bob = api.tokenFor("bob@example.com");
        UUID aliceWallet = api.createWallet(alice, "USD");
        UUID bobWallet = api.createWallet(bob, "USD");

        api.deposit(alice, aliceWallet, "d1", "100.00").andExpect(status().isCreated());
        api.withdraw(alice, aliceWallet, "w1", "10.00").andExpect(status().isCreated());
        api.transfer(alice, aliceWallet, bobWallet, "t1", "20.00").andExpect(status().isCreated());

        assertThat(rejections()).isEmpty();
    }

    @Test
    void movingMoneyInSomeoneElsesWalletIsAuditedAgainstTheActor() throws Exception {
        String alice = api.tokenFor("alice@example.com");
        String bob = api.tokenFor("bob@example.com");
        UUID aliceWallet = api.createWallet(alice, "USD");

        api.withdraw(bob, aliceWallet, "theft", "10.00").andExpect(status().isNotFound());

        List<Map<String, Object>> rows = rejections();
        assertThat(rows).hasSize(1);
        assertThat((UUID) rows.get(0).get("actor_user_id")).isEqualTo(idOf("bob@example.com"));
        assertThat((UUID) rows.get(0).get("target_id")).isEqualTo(aliceWallet);
        assertThat(rows.get(0).get("detail")).isEqualTo("withdrawals rejected with status 404");
    }

    @Test
    void aFrozenWalletRejectionIsAudited() throws Exception {
        String alice = api.tokenFor("alice@example.com");
        UUID wallet = api.createWallet(alice, "USD");
        jdbcTemplate.update("update wallets set status = 'FROZEN' where id = ?", wallet);

        api.deposit(alice, wallet, "d1", "10.00").andExpect(status().isConflict());

        List<Map<String, Object>> rows = rejections();
        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).get("detail")).isEqualTo("deposits rejected with status 409");
    }

    @Test
    void malformedInputIsNotAudited() throws Exception {
        String alice = api.tokenFor("alice@example.com");
        UUID wallet = api.createWallet(alice, "USD");

        api.deposit(alice, wallet, "d1", "0").andExpect(status().isBadRequest());
        api.deposit(alice, wallet, null, "10.00").andExpect(status().isBadRequest());

        assertThat(rejections()).isEmpty();
    }

    @Test
    void aRefusedTransferIsAuditedAgainstTheSourceWallet() throws Exception {
        String alice = api.tokenFor("alice@example.com");
        String bob = api.tokenFor("bob@example.com");
        UUID aliceWallet = api.createWallet(alice, "USD");
        UUID bobWallet = api.createWallet(bob, "USD");
        api.deposit(alice, aliceWallet, "d1", "100.00").andExpect(status().isCreated());

        api.transfer(alice, aliceWallet, bobWallet, "t1", "500.00").andExpect(status().isUnprocessableEntity());

        List<Map<String, Object>> rows = rejections();
        assertThat(rows).hasSize(1);
        assertThat((UUID) rows.get(0).get("target_id")).isEqualTo(aliceWallet);
        assertThat(rows.get(0).get("detail")).isEqualTo("transfers rejected with status 422");
    }

    @Test
    void unauthenticatedRequestsAreNotAudited() throws Exception {
        mvc.perform(post("/api/v1/wallets/" + UUID.randomUUID() + "/deposits")
                        .header("Idempotency-Key", "no-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\":\"10\"}"))
                .andExpect(status().isUnauthorized());

        assertThat(rejections()).isEmpty();
    }
}
