package com.ledgercore.transaction;

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

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.matchesPattern;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class StatementControllerIntegrationTest extends AbstractIntegrationTest {

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

    /** Alice's USD wallet gets four entries, oldest first: +100 deposit, -20 withdrawal, -30 transfer to Bob, +5 deposit. */
    private record Scenario(String alice, String bob, UUID aliceWallet, UUID bobWallet, String withdrawalReference) {
    }

    private Scenario fourEntries() throws Exception {
        String alice = api.tokenFor("alice@example.com");
        String bob = api.tokenFor("bob@example.com");
        UUID aliceWallet = api.createWallet(alice, "USD");
        UUID bobWallet = api.createWallet(bob, "USD");
        api.deposit(alice, aliceWallet, "d1", "100.00").andExpect(status().isCreated());
        String withdrawalReference = referenceOf(api.withdraw(alice, aliceWallet, "w1", "20.00"));
        api.transfer(alice, aliceWallet, bobWallet, "t1", "30.00").andExpect(status().isCreated());
        api.deposit(alice, aliceWallet, "d2", "5.00").andExpect(status().isCreated());
        return new Scenario(alice, bob, aliceWallet, bobWallet, withdrawalReference);
    }

    @Test
    void statementListsEntriesNewestFirstWithTheBalanceAfterEach() throws Exception {
        String alice = api.tokenFor("alice@example.com");
        UUID wallet = api.createWallet(alice, "USD");
        api.deposit(alice, wallet, "d1", "100.00").andExpect(status().isCreated());
        api.withdraw(alice, wallet, "w1", "30.00").andExpect(status().isCreated());
        api.deposit(alice, wallet, "d2", "5.50").andExpect(status().isCreated());

        api.statement(alice, wallet)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(20))
                .andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.totalPages").value(1))
                .andExpect(jsonPath("$.items.length()").value(3))
                .andExpect(jsonPath("$.items[0].reference").value(matchesPattern("TXN-[2-9A-HJKMNP-Z]{10}")))
                .andExpect(jsonPath("$.items[0].type").value("DEPOSIT"))
                .andExpect(jsonPath("$.items[0].direction").value("CREDIT"))
                .andExpect(jsonPath("$.items[0].amount").value("5.5000"))
                .andExpect(jsonPath("$.items[0].balanceAfter").value("75.5000"))
                .andExpect(jsonPath("$.items[0].counterpartyWalletId").value(nullValue()))
                .andExpect(jsonPath("$.items[1].type").value("WITHDRAWAL"))
                .andExpect(jsonPath("$.items[1].direction").value("DEBIT"))
                .andExpect(jsonPath("$.items[1].balanceAfter").value("70.0000"))
                .andExpect(jsonPath("$.items[2].balanceAfter").value("100.0000"));
    }

    @Test
    void pagesCoverEverythingExactlyOnceInOrder() throws Exception {
        String alice = api.tokenFor("alice@example.com");
        UUID wallet = api.createWallet(alice, "USD");
        for (int i = 1; i <= 25; i++) {
            api.deposit(alice, wallet, "dep-" + i, "1.00").andExpect(status().isCreated());
        }

        Set<String> references = new HashSet<>();
        List<String> balances = new ArrayList<>();
        for (int page = 0; page < 3; page++) {
            String body = api.statement(alice, wallet, "page", String.valueOf(page), "size", "10")
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.totalElements").value(25))
                    .andExpect(jsonPath("$.totalPages").value(3))
                    .andExpect(jsonPath("$.items.length()").value(page < 2 ? 10 : 5))
                    .andReturn().getResponse().getContentAsString();
            for (JsonNode item : objectMapper.readTree(body).get("items")) {
                references.add(item.get("reference").asText());
                balances.add(item.get("balanceAfter").asText());
            }
        }

        assertThat(references).hasSize(25);   // no row appeared twice
        List<String> expected = IntStream.rangeClosed(1, 25).boxed()
                .sorted(Comparator.reverseOrder()).map(i -> i + ".0000").toList();
        assertThat(balances).isEqualTo(expected);   // newest first across page boundaries

        api.statement(alice, wallet, "page", "3", "size", "10")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(0));
    }

    @Test
    void filterByTypeAndDirection() throws Exception {
        Scenario s = fourEntries();

        api.statement(s.alice(), s.aliceWallet(), "type", "DEPOSIT")
                .andExpect(jsonPath("$.totalElements").value(2));
        api.statement(s.alice(), s.aliceWallet(), "direction", "DEBIT")
                .andExpect(jsonPath("$.totalElements").value(2));
        api.statement(s.alice(), s.aliceWallet(), "type", "TRANSFER", "direction", "DEBIT")
                .andExpect(jsonPath("$.totalElements").value(1));
        api.statement(s.alice(), s.aliceWallet(), "type", "TRANSFER", "direction", "CREDIT")
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void dateRangeIncludesFromAndExcludesTo() throws Exception {
        Scenario s = fourEntries();
        List<String> times = jdbcTemplate.queryForList(
                "select to_char(created_at at time zone 'UTC', 'YYYY-MM-DD\"T\"HH24:MI:SS.US\"Z\"') "
                        + "from ledger_entries where wallet_id = ? order by created_at, id",
                String.class, s.aliceWallet());
        assertThat(times).hasSize(4);

        api.statement(s.alice(), s.aliceWallet(), "from", times.get(1))
                .andExpect(jsonPath("$.totalElements").value(3));   // the second entry itself is included
        api.statement(s.alice(), s.aliceWallet(), "to", times.get(1))
                .andExpect(jsonPath("$.totalElements").value(1));   // the second entry itself is excluded
        api.statement(s.alice(), s.aliceWallet(), "from", times.get(1), "to", times.get(3))
                .andExpect(jsonPath("$.totalElements").value(2));
    }

    @Test
    void searchByReference() throws Exception {
        Scenario s = fourEntries();

        api.statement(s.alice(), s.aliceWallet(), "reference", s.withdrawalReference().toLowerCase())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.items[0].type").value("WITHDRAWAL"));
        api.statement(s.alice(), s.aliceWallet(), "reference", "TXN-")
                .andExpect(jsonPath("$.totalElements").value(4));
    }

    @Test
    void aTransferShowsTheOtherWalletToBothParties() throws Exception {
        Scenario s = fourEntries();

        api.statement(s.alice(), s.aliceWallet(), "type", "TRANSFER")
                .andExpect(jsonPath("$.items[0].direction").value("DEBIT"))
                .andExpect(jsonPath("$.items[0].counterpartyWalletId").value(s.bobWallet().toString()));
        api.statement(s.bob(), s.bobWallet())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.items[0].direction").value("CREDIT"))
                .andExpect(jsonPath("$.items[0].balanceAfter").value("30.0000"))
                .andExpect(jsonPath("$.items[0].counterpartyWalletId").value(s.aliceWallet().toString()));
    }

    @Test
    void refusedRequestsLeaveNoTraceInTheStatement() throws Exception {
        String alice = api.tokenFor("alice@example.com");
        String bob = api.tokenFor("bob@example.com");
        UUID aliceWallet = api.createWallet(alice, "USD");
        UUID bobWallet = api.createWallet(bob, "USD");
        api.deposit(alice, aliceWallet, "d1", "100.00").andExpect(status().isCreated());

        api.withdraw(alice, aliceWallet, "w1", "500.00").andExpect(status().isUnprocessableEntity());
        api.transfer(alice, aliceWallet, bobWallet, "t1", "500.00").andExpect(status().isUnprocessableEntity());

        api.statement(alice, aliceWallet).andExpect(jsonPath("$.totalElements").value(1));
        api.statement(bob, bobWallet).andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void anotherUsersStatementIsNotFound() throws Exception {
        String alice = api.tokenFor("alice@example.com");
        String bob = api.tokenFor("bob@example.com");
        UUID aliceWallet = api.createWallet(alice, "USD");
        api.deposit(alice, aliceWallet, "d1", "100.00").andExpect(status().isCreated());

        api.statement(bob, aliceWallet).andExpect(status().isNotFound());
    }

    @Test
    void anUnknownWalletsStatementIsNotFound() throws Exception {
        String alice = api.tokenFor("alice@example.com");

        api.statement(alice, UUID.randomUUID()).andExpect(status().isNotFound());
    }

    @Test
    void historyEndpointsRequireAuthentication() throws Exception {
        mvc.perform(get("/api/v1/wallets/" + UUID.randomUUID() + "/transactions"))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/transactions/TXN-AAAAAAAAAA"))
                .andExpect(status().isUnauthorized());
    }

    @ParameterizedTest
    @CsvSource({
            "size, 0",
            "size, 101",
            "page, -1",
            "type, NOPE",
            "direction, SIDEWAYS",
            "from, not-a-date",
            "reference, TXN%"
    })
    void invalidFiltersAreABadRequest(String name, String value) throws Exception {
        String alice = api.tokenFor("alice@example.com");
        UUID wallet = api.createWallet(alice, "USD");

        api.statement(alice, wallet, name, value).andExpect(status().isBadRequest());
    }

    @Test
    void aDateRangeThatEndsBeforeItStartsIsABadRequest() throws Exception {
        String alice = api.tokenFor("alice@example.com");
        UUID wallet = api.createWallet(alice, "USD");

        api.statement(alice, wallet, "from", "2026-11-02T00:00:00Z", "to", "2026-11-01T00:00:00Z")
                .andExpect(status().isBadRequest());
    }

    @Test
    void ownerCanLookUpATransactionByReference() throws Exception {
        String alice = api.tokenFor("alice@example.com");
        UUID wallet = api.createWallet(alice, "USD");
        String reference = referenceOf(api.deposit(alice, wallet, "d1", "100.00"));

        api.transactionByReference(alice, reference)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reference").value(reference))
                .andExpect(jsonPath("$.type").value("DEPOSIT"))
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.amount").value("100.0000"))
                .andExpect(jsonPath("$.currency").value("USD"))
                .andExpect(jsonPath("$.sourceWalletId").value(nullValue()))
                .andExpect(jsonPath("$.destinationWalletId").value(wallet.toString()));
    }

    @Test
    void theRecipientOfATransferCanLookItUp() throws Exception {
        String alice = api.tokenFor("alice@example.com");
        String bob = api.tokenFor("bob@example.com");
        UUID aliceWallet = api.createWallet(alice, "USD");
        UUID bobWallet = api.createWallet(bob, "USD");
        api.deposit(alice, aliceWallet, "d1", "100.00").andExpect(status().isCreated());
        String reference = referenceOf(api.transfer(alice, aliceWallet, bobWallet, "t1", "40.00"));

        api.transactionByReference(bob, reference)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.type").value("TRANSFER"))
                .andExpect(jsonPath("$.amount").value("40.0000"))
                .andExpect(jsonPath("$.sourceWalletId").value(aliceWallet.toString()))
                .andExpect(jsonPath("$.destinationWalletId").value(bobWallet.toString()));
    }

    @Test
    void aStrangerCannotLookUpSomeoneElsesTransaction() throws Exception {
        String alice = api.tokenFor("alice@example.com");
        String carol = api.tokenFor("carol@example.com");
        UUID wallet = api.createWallet(alice, "USD");
        String reference = referenceOf(api.deposit(alice, wallet, "d1", "100.00"));

        api.transactionByReference(carol, reference).andExpect(status().isNotFound());
    }

    @Test
    void anUnknownReferenceIsNotFound() throws Exception {
        String alice = api.tokenFor("alice@example.com");

        api.transactionByReference(alice, "TXN-AAAAAAAAAA").andExpect(status().isNotFound());
    }

    @Test
    void aReferenceTypedInLowercaseStillWorks() throws Exception {
        String alice = api.tokenFor("alice@example.com");
        UUID wallet = api.createWallet(alice, "USD");
        String reference = referenceOf(api.deposit(alice, wallet, "d1", "100.00"));

        api.transactionByReference(alice, reference.toLowerCase()).andExpect(status().isOk());
    }
}
