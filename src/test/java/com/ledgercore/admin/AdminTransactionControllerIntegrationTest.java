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
import org.springframework.test.web.servlet.ResultActions;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AdminTransactionControllerIntegrationTest extends AbstractIntegrationTest {

    private static final String ADMIN_TRANSACTIONS = "/api/v1/admin/transactions";

    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private JdbcTemplate jdbcTemplate;

    private ApiClient api;

    @BeforeEach
    void setUpClient() {
        api = new ApiClient(mvc, objectMapper);
    }

    private String referenceOf(ResultActions result) throws Exception {
        String body = result.andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).get("reference").asText();
    }

    private UUID idOf(String email) {
        return jdbcTemplate.queryForObject("select id from users where email = ?", UUID.class, email);
    }

    /** Four transactions, oldest first: Alice deposits 100, Alice withdraws 20, Alice sends Bob 30, Bob deposits 5. */
    private record Scenario(String admin, UUID aliceWallet, UUID bobWallet, String withdrawalReference) {
    }

    private Scenario fourTransactions() throws Exception {
        String admin = api.tokenFor("admin@example.com");
        promoteToAdmin("admin@example.com");
        String alice = api.tokenFor("alice@example.com");
        String bob = api.tokenFor("bob@example.com");
        UUID aliceWallet = api.createWallet(alice, "USD");
        UUID bobWallet = api.createWallet(bob, "USD");
        api.deposit(alice, aliceWallet, "a1", "100.00").andExpect(status().isCreated());
        String withdrawalReference = referenceOf(api.withdraw(alice, aliceWallet, "a2", "20.00"));
        api.transfer(alice, aliceWallet, bobWallet, "a3", "30.00").andExpect(status().isCreated());
        api.deposit(bob, bobWallet, "b1", "5.00").andExpect(status().isCreated());
        return new Scenario(admin, aliceWallet, bobWallet, withdrawalReference);
    }

    @Test
    void aNonAdminIsForbidden() throws Exception {
        String alice = api.tokenFor("alice@example.com");

        api.getAs(alice, ADMIN_TRANSACTIONS).andExpect(status().isForbidden());
    }

    @Test
    void theListShowsEveryonesTransactionsNewestFirst() throws Exception {
        Scenario s = fourTransactions();

        api.getAs(s.admin(), ADMIN_TRANSACTIONS)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(4))
                .andExpect(jsonPath("$.items[0].type").value("DEPOSIT"))
                .andExpect(jsonPath("$.items[0].userId").value(idOf("bob@example.com").toString()))
                .andExpect(jsonPath("$.items[0].sourceWalletId").value(nullValue()))
                .andExpect(jsonPath("$.items[0].destinationWalletId").value(s.bobWallet().toString()))
                .andExpect(jsonPath("$.items[1].type").value("TRANSFER"))
                .andExpect(jsonPath("$.items[1].status").value("COMPLETED"))
                .andExpect(jsonPath("$.items[1].amount").value("30.0000"))
                .andExpect(jsonPath("$.items[1].currency").value("USD"))
                .andExpect(jsonPath("$.items[1].userId").value(idOf("alice@example.com").toString()))
                .andExpect(jsonPath("$.items[1].sourceWalletId").value(s.aliceWallet().toString()))
                .andExpect(jsonPath("$.items[1].destinationWalletId").value(s.bobWallet().toString()))
                .andExpect(jsonPath("$.items[2].type").value("WITHDRAWAL"))
                .andExpect(jsonPath("$.items[3].type").value("DEPOSIT"));
    }

    @Test
    void filterByType() throws Exception {
        Scenario s = fourTransactions();

        api.getAs(s.admin(), ADMIN_TRANSACTIONS, "type", "DEPOSIT").andExpect(jsonPath("$.totalElements").value(2));
        api.getAs(s.admin(), ADMIN_TRANSACTIONS, "type", "WITHDRAWAL").andExpect(jsonPath("$.totalElements").value(1));
        api.getAs(s.admin(), ADMIN_TRANSACTIONS, "type", "TRANSFER").andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void filterByWalletMatchesEitherSideOfATransfer() throws Exception {
        Scenario s = fourTransactions();

        api.getAs(s.admin(), ADMIN_TRANSACTIONS, "walletId", s.aliceWallet().toString())
                .andExpect(jsonPath("$.totalElements").value(3));   // her deposit, her withdrawal, the transfer she sent
        api.getAs(s.admin(), ADMIN_TRANSACTIONS, "walletId", s.bobWallet().toString())
                .andExpect(jsonPath("$.totalElements").value(2));   // the transfer he received, his own deposit
    }

    @Test
    void filterByTheUserWhoMadeTheRequest() throws Exception {
        Scenario s = fourTransactions();

        api.getAs(s.admin(), ADMIN_TRANSACTIONS, "userId", idOf("alice@example.com").toString())
                .andExpect(jsonPath("$.totalElements").value(3));
        api.getAs(s.admin(), ADMIN_TRANSACTIONS, "userId", idOf("bob@example.com").toString())
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void searchByReferenceIgnoresCase() throws Exception {
        Scenario s = fourTransactions();

        api.getAs(s.admin(), ADMIN_TRANSACTIONS, "reference", s.withdrawalReference().toLowerCase())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.items[0].type").value("WITHDRAWAL"));
    }

    @Test
    void dateRangesFilterByCreationTime() throws Exception {
        Scenario s = fourTransactions();

        api.getAs(s.admin(), ADMIN_TRANSACTIONS, "from", "2999-01-01T00:00:00Z")
                .andExpect(jsonPath("$.totalElements").value(0));
        api.getAs(s.admin(), ADMIN_TRANSACTIONS, "to", "2000-01-01T00:00:00Z")
                .andExpect(jsonPath("$.totalElements").value(0));
        api.getAs(s.admin(), ADMIN_TRANSACTIONS, "from", "2000-01-01T00:00:00Z")
                .andExpect(jsonPath("$.totalElements").value(4));
    }

    @Test
    void pagingCoversEveryTransactionExactlyOnce() throws Exception {
        Scenario s = fourTransactions();

        Set<String> references = new HashSet<>();
        for (int page = 0; page < 2; page++) {
            String body = api.getAs(s.admin(), ADMIN_TRANSACTIONS, "page", String.valueOf(page), "size", "2")
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.totalElements").value(4))
                    .andExpect(jsonPath("$.totalPages").value(2))
                    .andExpect(jsonPath("$.items.length()").value(2))
                    .andReturn().getResponse().getContentAsString();
            for (JsonNode item : objectMapper.readTree(body).get("items")) {
                references.add(item.get("reference").asText());
            }
        }
        assertThat(references).hasSize(4);
        api.getAs(s.admin(), ADMIN_TRANSACTIONS, "page", "2", "size", "2")
                .andExpect(jsonPath("$.items.length()").value(0));
    }

    @ParameterizedTest
    @CsvSource({
            "size, 0",
            "type, NOPE",
            "walletId, not-a-uuid",
            "userId, not-a-uuid",
            "reference, TXN%"
    })
    void invalidFiltersAreABadRequest(String name, String value) throws Exception {
        String admin = api.tokenFor("admin@example.com");
        promoteToAdmin("admin@example.com");

        api.getAs(admin, ADMIN_TRANSACTIONS, name, value).andExpect(status().isBadRequest());
    }

    @Test
    void aDateRangeThatEndsBeforeItStartsIsABadRequest() throws Exception {
        String admin = api.tokenFor("admin@example.com");
        promoteToAdmin("admin@example.com");

        api.getAs(admin, ADMIN_TRANSACTIONS, "from", "2026-11-02T00:00:00Z", "to", "2026-11-01T00:00:00Z")
                .andExpect(status().isBadRequest());
    }
}
