package com.ledgercore.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ledgercore.AbstractIntegrationTest;
import com.ledgercore.user.User;
import com.ledgercore.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AuthControllerIT extends AbstractIntegrationTest {

    private static final String EMAIL = "alice@example.com";
    private static final String PASSWORD = "correct-horse-battery";

    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private UserRepository userRepository;

    @BeforeEach
    void cleanDatabase() {
        userRepository.deleteAll();
    }

    private ResultActions register(String email, String password, String fullName) throws Exception {
        return mvc.perform(post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(
                        Map.of("email", email, "password", password, "fullName", fullName))));
    }

    private ResultActions login(String email, String password) throws Exception {
        return mvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("email", email, "password", password))));
    }

    private String registerAndLogin() throws Exception {
        register(EMAIL, PASSWORD, "Alice").andExpect(status().isCreated());
        String body = login(EMAIL, PASSWORD).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).get("accessToken").asText();
    }

    @Test
    void registerCreatesUserAndNeverExposesPassword() throws Exception {
        register("Alice@Example.com", PASSWORD, "Alice")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.email").value(EMAIL))
                .andExpect(jsonPath("$.role").value("USER"))
                .andExpect(jsonPath("$.password").doesNotExist())
                .andExpect(jsonPath("$.passwordHash").doesNotExist());
    }

    @Test
    void passwordIsStoredAsBcryptHash() throws Exception {
        register(EMAIL, PASSWORD, "Alice").andExpect(status().isCreated());

        User stored = userRepository.findByEmail(EMAIL).orElseThrow();

        assertThat(stored.getPasswordHash()).isNotEqualTo(PASSWORD).startsWith("$2");
    }

    @Test
    void duplicateEmailIsRejectedCaseInsensitively() throws Exception {
        register(EMAIL, PASSWORD, "Alice").andExpect(status().isCreated());

        register("ALICE@example.com", PASSWORD, "Impostor")
                .andExpect(status().isConflict());
    }

    @Test
    void invalidRegistrationReturnsFieldErrors() throws Exception {
        register("not-an-email", "short", " ")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors").isArray())
                .andExpect(jsonPath("$.errors.length()").value(3));
    }

    @Test
    void loginReturnsBearerToken() throws Exception {
        register(EMAIL, PASSWORD, "Alice").andExpect(status().isCreated());

        login(EMAIL, PASSWORD)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.tokenType").value("Bearer"));
    }

    @Test
    void wrongPasswordAndUnknownEmailAreIndistinguishable() throws Exception {
        register(EMAIL, PASSWORD, "Alice").andExpect(status().isCreated());

        String wrongPassword = login(EMAIL, "not-the-password")
                .andExpect(status().isUnauthorized())
                .andReturn().getResponse().getContentAsString();
        String unknownEmail = login("nobody@example.com", PASSWORD)
                .andExpect(status().isUnauthorized())
                .andReturn().getResponse().getContentAsString();

        assertThat(objectMapper.readTree(wrongPassword).get("detail"))
                .isEqualTo(objectMapper.readTree(unknownEmail).get("detail"));
    }

    @Test
    void protectedEndpointRequiresToken() throws Exception {
        mvc.perform(get("/api/v1/users/me"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void validTokenGrantsAccessToCurrentUser() throws Exception {
        String token = registerAndLogin();

        mvc.perform(get("/api/v1/users/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(EMAIL));
    }

    @Test
    void tamperedTokenIsRejected() throws Exception {
        String token = registerAndLogin();
        String tampered = token.substring(0, token.length() - 4) + "AAAA";

        mvc.perform(get("/api/v1/users/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + tampered))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void disabledUserCannotLoginOrUseExistingToken() throws Exception {
        String token = registerAndLogin();
        User user = userRepository.findByEmail(EMAIL).orElseThrow();
        user.setEnabled(false);
        userRepository.saveAndFlush(user);

        mvc.perform(get("/api/v1/users/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isUnauthorized());
        login(EMAIL, PASSWORD).andExpect(status().isUnauthorized());
    }
}
