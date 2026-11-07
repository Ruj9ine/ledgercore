package com.ledgercore.admin;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ledgercore.AbstractIntegrationTest;
import com.ledgercore.ApiClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AdminAuditControllerIntegrationTest extends AbstractIntegrationTest {

    private static final String AUDIT_LOG = "/api/v1/admin/audit-log";

    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private JdbcTemplate jdbcTemplate;

    private ApiClient api;

    @BeforeEach
    void setUpClient() {
        api = new ApiClient(mvc, objectMapper);
    }

    private UUID idOf(String email) {
        return jdbcTemplate.queryForObject("select id from users where email = ?", UUID.class, email);
    }

    private record Scenario(String admin, UUID adminId, UUID bobId, UUID aliceWallet) {
    }

    /** Creates three audit entries, oldest first: Bob is denied an admin page, the admin freezes Alice's wallet, then disables Bob. */
    private Scenario threeAuditedEvents() throws Exception {
        String admin = api.tokenFor("admin@example.com");
        promoteToAdmin("admin@example.com");
        String alice = api.tokenFor("alice@example.com");
        String bob = api.tokenFor("bob@example.com");
        UUID aliceWallet = api.createWallet(alice, "USD");
        UUID bobId = idOf("bob@example.com");

        api.getAs(bob, "/api/v1/admin/users").andExpect(status().isForbidden());
        api.postAs(admin, "/api/v1/admin/wallets/" + aliceWallet + "/freeze", "{\"reason\":\"fraud review\"}")
                .andExpect(status().isOk());
        api.postAs(admin, "/api/v1/admin/users/" + bobId + "/disable", "{\"reason\":\"bob misuse\"}")
                .andExpect(status().isOk());
        return new Scenario(admin, idOf("admin@example.com"), bobId, aliceWallet);
    }

    @Test
    void aNonAdminIsForbidden() throws Exception {
        String alice = api.tokenFor("alice@example.com");

        api.getAs(alice, AUDIT_LOG).andExpect(status().isForbidden());
    }

    @Test
    void theAuditLogRequiresAuthentication() throws Exception {
        mvc.perform(get(AUDIT_LOG)).andExpect(status().isUnauthorized());
    }

    @Test
    void entriesAreListedNewestFirstWithEveryField() throws Exception {
        Scenario s = threeAuditedEvents();

        api.getAs(s.admin(), AUDIT_LOG)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.items[0].action").value("USER_DISABLED"))
                .andExpect(jsonPath("$.items[0].id").isNotEmpty())
                .andExpect(jsonPath("$.items[0].occurredAt").isNotEmpty())
                .andExpect(jsonPath("$.items[0].actorUserId").value(s.adminId().toString()))
                .andExpect(jsonPath("$.items[0].targetType").value("USER"))
                .andExpect(jsonPath("$.items[0].targetId").value(s.bobId().toString()))
                .andExpect(jsonPath("$.items[0].reason").value("bob misuse"))
                .andExpect(jsonPath("$.items[0].detail").value("was enabled"))
                .andExpect(jsonPath("$.items[1].action").value("WALLET_FROZEN"))
                .andExpect(jsonPath("$.items[2].action").value("ADMIN_ACCESS_DENIED"))
                .andExpect(jsonPath("$.items[2].actorUserId").value(s.bobId().toString()));
    }

    @Test
    void filterByAction() throws Exception {
        Scenario s = threeAuditedEvents();

        api.getAs(s.admin(), AUDIT_LOG, "action", "WALLET_FROZEN")
                .andExpect(jsonPath("$.totalElements").value(1));
        api.getAs(s.admin(), AUDIT_LOG, "action", "USER_ENABLED")
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void filterByActor() throws Exception {
        Scenario s = threeAuditedEvents();

        api.getAs(s.admin(), AUDIT_LOG, "actorUserId", s.adminId().toString())
                .andExpect(jsonPath("$.totalElements").value(2));
        api.getAs(s.admin(), AUDIT_LOG, "actorUserId", s.bobId().toString())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.items[0].action").value("ADMIN_ACCESS_DENIED"));
    }

    @Test
    void filterByTarget() throws Exception {
        Scenario s = threeAuditedEvents();

        api.getAs(s.admin(), AUDIT_LOG, "targetId", s.aliceWallet().toString())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.items[0].action").value("WALLET_FROZEN"));
        api.getAs(s.admin(), AUDIT_LOG, "targetId", s.bobId().toString())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.items[0].action").value("USER_DISABLED"));
    }

    @Test
    void dateRangesFilterByWhenTheEventHappened() throws Exception {
        Scenario s = threeAuditedEvents();

        api.getAs(s.admin(), AUDIT_LOG, "from", "2999-01-01T00:00:00Z").andExpect(jsonPath("$.totalElements").value(0));
        api.getAs(s.admin(), AUDIT_LOG, "to", "2000-01-01T00:00:00Z").andExpect(jsonPath("$.totalElements").value(0));
        api.getAs(s.admin(), AUDIT_LOG, "from", "2000-01-01T00:00:00Z").andExpect(jsonPath("$.totalElements").value(3));
    }

    @Test
    void pagingCoversEveryEntryExactlyOnce() throws Exception {
        Scenario s = threeAuditedEvents();

        Set<String> ids = new HashSet<>();
        for (int page = 0; page < 3; page++) {
            String body = api.getAs(s.admin(), AUDIT_LOG, "page", String.valueOf(page), "size", "1")
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.totalElements").value(3))
                    .andExpect(jsonPath("$.totalPages").value(3))
                    .andExpect(jsonPath("$.items.length()").value(1))
                    .andReturn().getResponse().getContentAsString();
            for (JsonNode item : objectMapper.readTree(body).get("items")) {
                ids.add(item.get("id").asText());
            }
        }
        assertThat(ids).hasSize(3);
    }

    @ParameterizedTest
    @CsvSource({
            "size, 0",
            "action, NOPE",
            "actorUserId, not-a-uuid",
            "from, not-a-date"
    })
    void invalidFiltersAreABadRequest(String name, String value) throws Exception {
        String admin = api.tokenFor("admin@example.com");
        promoteToAdmin("admin@example.com");

        api.getAs(admin, AUDIT_LOG, name, value).andExpect(status().isBadRequest());
    }

    @Test
    void aDateRangeThatEndsBeforeItStartsIsABadRequest() throws Exception {
        String admin = api.tokenFor("admin@example.com");
        promoteToAdmin("admin@example.com");

        api.getAs(admin, AUDIT_LOG, "from", "2026-11-02T00:00:00Z", "to", "2026-11-01T00:00:00Z")
                .andExpect(status().isBadRequest());
    }
}
