package com.ledgercore.admin;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ledgercore.AbstractIntegrationTest;
import com.ledgercore.ApiClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AdminUserControllerIntegrationTest extends AbstractIntegrationTest {

    private static final String ADMIN_USERS = "/api/v1/admin/users";

    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private JdbcTemplate jdbcTemplate;

    private ApiClient api;

    @BeforeEach
    void setUpClient() {
        api = new ApiClient(mvc, objectMapper);
    }

    /** Registers a user and promotes them directly in the database, which is how admins are made outside bootstrap. */
    private String adminToken(String email) throws Exception {
        String token = api.tokenFor(email);
        jdbcTemplate.update("update users set role = 'ADMIN' where email = ?", email);
        return token;
    }

    private UUID idOf(String email) {
        return jdbcTemplate.queryForObject("select id from users where email = ?", UUID.class, email);
    }

    private boolean isEnabled(String email) {
        return jdbcTemplate.queryForObject("select enabled from users where email = ?", Boolean.class, email);
    }

    private long auditCount(String action) {
        return jdbcTemplate.queryForObject("select count(*) from audit_log where action = ?", Long.class, action);
    }

    private String reason(String text) {
        return "{\"reason\":\"" + text + "\"}";
    }

    @Test
    void aNonAdminIsForbiddenOnEveryAdminEndpointAndEachAttemptIsAudited() throws Exception {
        String alice = api.tokenFor("alice@example.com");
        UUID aliceId = idOf("alice@example.com");

        api.getAs(alice, ADMIN_USERS).andExpect(status().isForbidden()).andExpect(jsonPath("$.title").value("Forbidden"));
        api.getAs(alice, ADMIN_USERS + "/" + aliceId).andExpect(status().isForbidden());
        api.postAs(alice, ADMIN_USERS + "/" + aliceId + "/disable", reason("x")).andExpect(status().isForbidden());
        api.postAs(alice, ADMIN_USERS + "/" + aliceId + "/enable", reason("x")).andExpect(status().isForbidden());

        assertThat(isEnabled("alice@example.com")).isTrue();
        Long deniedByAlice = jdbcTemplate.queryForObject(
                "select count(*) from audit_log where action = 'ADMIN_ACCESS_DENIED' and actor_user_id = ?",
                Long.class, aliceId);
        assertThat(deniedByAlice).isEqualTo(4);
    }

    @Test
    void adminEndpointsRequireAuthentication() throws Exception {
        mvc.perform(get(ADMIN_USERS)).andExpect(status().isUnauthorized());
        mvc.perform(post(ADMIN_USERS + "/" + UUID.randomUUID() + "/disable")
                        .contentType(MediaType.APPLICATION_JSON).content(reason("x")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void theListIsNewestFirst() throws Exception {
        String admin = adminToken("admin@example.com");
        api.tokenFor("u1@example.com");
        api.tokenFor("u2@example.com");

        api.getAs(admin, ADMIN_USERS)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.items[0].email").value("u2@example.com"))
                .andExpect(jsonPath("$.items[1].email").value("u1@example.com"))
                .andExpect(jsonPath("$.items[2].email").value("admin@example.com"))
                .andExpect(jsonPath("$.items[2].role").value("ADMIN"))
                .andExpect(jsonPath("$.items[0].role").value("USER"))
                .andExpect(jsonPath("$.items[0].enabled").value(true));
    }

    @Test
    void theListFiltersByRoleEnabledAndEmail() throws Exception {
        String admin = adminToken("admin@example.com");
        api.tokenFor("alice@example.com");
        api.tokenFor("bob@example.com");
        jdbcTemplate.update("update users set enabled = false where email = 'bob@example.com'");

        api.getAs(admin, ADMIN_USERS, "role", "ADMIN").andExpect(jsonPath("$.totalElements").value(1));
        api.getAs(admin, ADMIN_USERS, "role", "USER").andExpect(jsonPath("$.totalElements").value(2));
        api.getAs(admin, ADMIN_USERS, "enabled", "false").andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.items[0].email").value("bob@example.com"));
        api.getAs(admin, ADMIN_USERS, "enabled", "true").andExpect(jsonPath("$.totalElements").value(2));
        api.getAs(admin, ADMIN_USERS, "email", "ALI").andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.items[0].email").value("alice@example.com"));
        api.getAs(admin, ADMIN_USERS, "role", "USER", "enabled", "true").andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void anEmailSearchTreatsUnderscoreAndPercentLiterally() throws Exception {
        String admin = adminToken("admin@example.com");
        api.tokenFor("a_b@example.com");
        api.tokenFor("axb@example.com");

        api.getAs(admin, ADMIN_USERS, "email", "a_b")
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.items[0].email").value("a_b@example.com"));
        api.getAs(admin, ADMIN_USERS, "email", "%").andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void pagingCoversEveryUserExactlyOnce() throws Exception {
        String admin = adminToken("admin@example.com");
        for (int i = 1; i <= 4; i++) {
            api.tokenFor("user" + i + "@example.com");
        }

        Set<String> emails = new HashSet<>();
        for (int page = 0; page < 3; page++) {
            String body = api.getAs(admin, ADMIN_USERS, "page", String.valueOf(page), "size", "2")
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.totalElements").value(5))
                    .andExpect(jsonPath("$.totalPages").value(3))
                    .andExpect(jsonPath("$.items.length()").value(page < 2 ? 2 : 1))
                    .andReturn().getResponse().getContentAsString();
            for (JsonNode item : objectMapper.readTree(body).get("items")) {
                emails.add(item.get("email").asText());
            }
        }
        assertThat(emails).hasSize(5);
    }

    @Test
    void anAdminCanViewAUser() throws Exception {
        String admin = adminToken("admin@example.com");
        api.tokenFor("alice@example.com");

        api.getAs(admin, ADMIN_USERS + "/" + idOf("alice@example.com"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("alice@example.com"))
                .andExpect(jsonPath("$.fullName").value("Test User"))
                .andExpect(jsonPath("$.role").value("USER"))
                .andExpect(jsonPath("$.enabled").value(true))
                .andExpect(jsonPath("$.createdAt").isNotEmpty());
    }

    @Test
    void viewingAnUnknownUserIsNotFound() throws Exception {
        String admin = adminToken("admin@example.com");

        api.getAs(admin, ADMIN_USERS + "/" + UUID.randomUUID()).andExpect(status().isNotFound());
    }

    @Test
    void aMalformedUserIdIsABadRequest() throws Exception {
        String admin = adminToken("admin@example.com");

        api.getAs(admin, ADMIN_USERS + "/not-a-uuid").andExpect(status().isBadRequest());
    }

    @Test
    void disablingBlocksLoginAndExistingTokensImmediatelyAndIsAudited() throws Exception {
        String admin = adminToken("admin@example.com");
        String alice = api.tokenFor("alice@example.com");
        UUID aliceId = idOf("alice@example.com");
        api.getAs(alice, "/api/v1/users/me").andExpect(status().isOk());

        api.postAs(admin, ADMIN_USERS + "/" + aliceId + "/disable", reason("  suspected account takeover "))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(false));

        api.getAs(alice, "/api/v1/users/me").andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("email", "alice@example.com", "password", ApiClient.PASSWORD))))
                .andExpect(status().isUnauthorized());

        Map<String, Object> row = jdbcTemplate.queryForMap(
                "select actor_user_id, target_type, target_id, reason, detail from audit_log where action = 'USER_DISABLED'");
        assertThat((UUID) row.get("actor_user_id")).isEqualTo(idOf("admin@example.com"));
        assertThat(row.get("target_type")).isEqualTo("USER");
        assertThat((UUID) row.get("target_id")).isEqualTo(aliceId);
        assertThat(row.get("reason")).isEqualTo("suspected account takeover");
        assertThat(row.get("detail")).isEqualTo("was enabled");
    }

    @Test
    void enablingRestoresAccessAndIsAudited() throws Exception {
        String admin = adminToken("admin@example.com");
        String alice = api.tokenFor("alice@example.com");
        UUID aliceId = idOf("alice@example.com");
        api.postAs(admin, ADMIN_USERS + "/" + aliceId + "/disable", reason("under review")).andExpect(status().isOk());
        api.getAs(alice, "/api/v1/users/me").andExpect(status().isUnauthorized());

        api.postAs(admin, ADMIN_USERS + "/" + aliceId + "/enable", reason("review finished"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(true));

        api.getAs(alice, "/api/v1/users/me").andExpect(status().isOk());   // the old token works again
        assertThat(auditCount("USER_DISABLED")).isEqualTo(1);
        assertThat(auditCount("USER_ENABLED")).isEqualTo(1);
    }

    @Test
    void disablingTwiceIsAConflictWithoutASecondAuditEntry() throws Exception {
        String admin = adminToken("admin@example.com");
        api.tokenFor("alice@example.com");
        UUID aliceId = idOf("alice@example.com");

        api.postAs(admin, ADMIN_USERS + "/" + aliceId + "/disable", reason("first")).andExpect(status().isOk());
        api.postAs(admin, ADMIN_USERS + "/" + aliceId + "/disable", reason("second")).andExpect(status().isConflict());

        assertThat(auditCount("USER_DISABLED")).isEqualTo(1);
    }

    @Test
    void anAdminCannotDisableThemselves() throws Exception {
        String admin = adminToken("admin@example.com");

        api.postAs(admin, ADMIN_USERS + "/" + idOf("admin@example.com") + "/disable", reason("oops"))
                .andExpect(status().isConflict());

        assertThat(isEnabled("admin@example.com")).isTrue();
        assertThat(auditCount("USER_DISABLED")).isZero();
    }

    @Test
    void invalidReasonsAreRejectedAndChangeNothing() throws Exception {
        String admin = adminToken("admin@example.com");
        api.tokenFor("alice@example.com");
        String path = ADMIN_USERS + "/" + idOf("alice@example.com") + "/disable";

        api.postAs(admin, path, "{}").andExpect(status().isBadRequest());
        api.postAs(admin, path, reason("   ")).andExpect(status().isBadRequest());
        api.postAs(admin, path, reason("x".repeat(501))).andExpect(status().isBadRequest());

        assertThat(isEnabled("alice@example.com")).isTrue();
        assertThat(auditCount("USER_DISABLED")).isZero();
    }

    @Test
    void disablingAnUnknownUserIsNotFoundAndWritesNoAudit() throws Exception {
        String admin = adminToken("admin@example.com");

        api.postAs(admin, ADMIN_USERS + "/" + UUID.randomUUID() + "/disable", reason("who"))
                .andExpect(status().isNotFound());

        assertThat(auditCount("USER_DISABLED")).isZero();
    }

    @Test
    void aDemotedAdminLosesAccessOnTheVeryNextRequest() throws Exception {
        String admin = adminToken("admin@example.com");
        api.getAs(admin, ADMIN_USERS).andExpect(status().isOk());

        jdbcTemplate.update("update users set role = 'USER' where email = 'admin@example.com'");

        api.getAs(admin, ADMIN_USERS).andExpect(status().isForbidden());   // the same token, no longer an admin
        assertThat(auditCount("ADMIN_ACCESS_DENIED")).isEqualTo(1);
    }

    @Test
    void responsesNeverContainPasswordFields() throws Exception {
        String admin = adminToken("admin@example.com");
        api.tokenFor("alice@example.com");

        api.getAs(admin, ADMIN_USERS).andExpect(content().string(not(containsString("password"))));
        api.getAs(admin, ADMIN_USERS + "/" + idOf("alice@example.com"))
                .andExpect(content().string(not(containsString("password"))));
    }
}
