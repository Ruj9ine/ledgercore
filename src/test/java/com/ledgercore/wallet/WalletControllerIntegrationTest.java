package com.ledgercore.wallet;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ledgercore.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.util.Map;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class WalletControllerIntegrationTest extends AbstractIntegrationTest {

    private static final String PASSWORD = "correct-horse-battery";

    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper objectMapper;

    private String tokenFor(String email) throws Exception {
        mvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("email", email, "password", PASSWORD, "fullName", "Test User"))))
                .andExpect(status().isCreated());
        String body = mvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("email", email, "password", PASSWORD))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).get("accessToken").asText();
    }

    private ResultActions createWallet(String token, Object currency) throws Exception {
        return mvc.perform(post("/api/v1/wallets")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("currency", currency))));
    }

    private String createWalletAndGetId(String token, String currency) throws Exception {
        String body = createWallet(token, currency).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).get("id").asText();
    }

    private ResultActions getWallet(String token, String id) throws Exception {
        return mvc.perform(get("/api/v1/wallets/" + id).header(HttpHeaders.AUTHORIZATION, "Bearer " + token));
    }

    @Test
    void newWalletStartsActiveWithZeroBalance() throws Exception {
        String token = tokenFor("alice@example.com");

        createWallet(token, "UGX")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNotEmpty())
                .andExpect(jsonPath("$.currency").value("UGX"))
                .andExpect(jsonPath("$.balance").value("0.0000"))
                .andExpect(jsonPath("$.status").value("ACTIVE"));
    }

    @Test
    void secondWalletInTheSameCurrencyIsRejectedButAnotherCurrencyIsAllowed() throws Exception {
        String token = tokenFor("alice@example.com");
        createWalletAndGetId(token, "UGX");

        createWallet(token, "UGX").andExpect(status().isConflict());
        createWallet(token, "USD").andExpect(status().isCreated());
    }

    @Test
    void unsupportedCurrencyIsRejected() throws Exception {
        String token = tokenFor("alice@example.com");

        createWallet(token, "XYZ").andExpect(status().isBadRequest());
    }

    @Test
    void missingCurrencyIsRejectedWithFieldErrors() throws Exception {
        String token = tokenFor("alice@example.com");

        mvc.perform(post("/api/v1/wallets")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors").isArray());
    }

    @Test
    void listShowsOnlyTheCallersWallets() throws Exception {
        String alice = tokenFor("alice@example.com");
        String bob = tokenFor("bob@example.com");
        createWalletAndGetId(alice, "UGX");
        createWalletAndGetId(bob, "USD");

        mvc.perform(get("/api/v1/wallets").header(HttpHeaders.AUTHORIZATION, "Bearer " + alice))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].currency").value("UGX"));
    }

    @Test
    void ownerCanReadTheirWallet() throws Exception {
        String token = tokenFor("alice@example.com");
        String id = createWalletAndGetId(token, "UGX");

        getWallet(token, id)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id));
    }

    @Test
    void anotherUsersWalletLooksLikeItDoesNotExist() throws Exception {
        String alice = tokenFor("alice@example.com");
        String bob = tokenFor("bob@example.com");
        String aliceWalletId = createWalletAndGetId(alice, "UGX");

        getWallet(bob, aliceWalletId).andExpect(status().isNotFound());
    }

    @Test
    void unknownWalletIdIsNotFound() throws Exception {
        String token = tokenFor("alice@example.com");

        getWallet(token, UUID.randomUUID().toString()).andExpect(status().isNotFound());
    }

    @Test
    void malformedWalletIdIsABadRequest() throws Exception {
        String token = tokenFor("alice@example.com");

        getWallet(token, "not-a-uuid").andExpect(status().isBadRequest());
    }

    @Test
    void walletEndpointsRequireAuthentication() throws Exception {
        mvc.perform(get("/api/v1/wallets")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/wallets")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currency\":\"UGX\"}"))
                .andExpect(status().isUnauthorized());
    }
}
