package com.ledgercore;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.Map;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Drives the API the way a client would, so integration tests read as scenarios instead of plumbing. */
public class ApiClient {

    public static final String PASSWORD = "correct-horse-battery";

    private final MockMvc mvc;
    private final ObjectMapper objectMapper;

    public ApiClient(MockMvc mvc, ObjectMapper objectMapper) {
        this.mvc = mvc;
        this.objectMapper = objectMapper;
    }

    /** Registers a user and returns a bearer token for them. */
    public String tokenFor(String email) throws Exception {
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

    public UUID createWallet(String token, String currency) throws Exception {
        String body = mvc.perform(post("/api/v1/wallets")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("currency", currency))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return UUID.fromString(objectMapper.readTree(body).get("id").asText());
    }

    public ResultActions deposit(String token, UUID walletId, String key, String amount) throws Exception {
        return movement(token, walletId, "deposits", key, "{\"amount\":\"" + amount + "\"}");
    }

    public ResultActions withdraw(String token, UUID walletId, String key, String amount) throws Exception {
        return movement(token, walletId, "withdrawals", key, "{\"amount\":\"" + amount + "\"}");
    }

    /** The destination is an Object so tests can also pass something that is not a valid UUID. */
    public ResultActions transfer(String token, UUID sourceWalletId, Object destinationWalletId,
                                  String key, String amount) throws Exception {
        String body = "{\"destinationWalletId\":\"" + destinationWalletId + "\",\"amount\":\"" + amount + "\"}";
        return movement(token, sourceWalletId, "transfers", key, body);
    }

    public String balanceOf(String token, UUID walletId) throws Exception {
        String body = mvc.perform(get("/api/v1/wallets/" + walletId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).get("balance").asText();
    }

    private ResultActions movement(String token, UUID walletId, String path, String key, String body) throws Exception {
        MockHttpServletRequestBuilder request = post("/api/v1/wallets/" + walletId + "/" + path)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body);
        if (key != null) {
            request.header("Idempotency-Key", key);
        }
        return mvc.perform(request);
    }
}
