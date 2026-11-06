package com.ledgercore.transaction;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ledgercore.AbstractIntegrationTest;
import com.ledgercore.ApiClient;
import com.ledgercore.wallet.Wallet;
import com.ledgercore.wallet.WalletRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class TransferControllerIntegrationTest extends AbstractIntegrationTest {

    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private WalletRepository walletRepository;

    private ApiClient api;

    @BeforeEach
    void setUpClient() {
        api = new ApiClient(mvc, objectMapper);
    }

    private long count(String table) {
        return jdbcTemplate.queryForObject("select count(*) from " + table, Long.class);
    }

    private void freeze(UUID walletId) {
        Wallet wallet = walletRepository.findById(walletId).orElseThrow();
        wallet.freeze();
        walletRepository.saveAndFlush(wallet);
    }

    @Test
    void transferMovesMoneyAndShowsOnlyTheSendersBalance() throws Exception {
        String alice = api.tokenFor("alice@example.com");
        String bob = api.tokenFor("bob@example.com");
        UUID aliceWallet = api.createWallet(alice, "UGX");
        UUID bobWallet = api.createWallet(bob, "UGX");
        api.deposit(alice, aliceWallet, "dep-1", "5000").andExpect(status().isCreated());

        api.transfer(alice, aliceWallet, bobWallet, "tr-1", "1500")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.reference").value(matchesPattern("TXN-[2-9A-HJKMNP-Z]{10}")))
                .andExpect(jsonPath("$.type").value("TRANSFER"))
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.amount").value("1500.0000"))
                .andExpect(jsonPath("$.sourceWalletId").value(aliceWallet.toString()))
                .andExpect(jsonPath("$.destinationWalletId").value(bobWallet.toString()))
                .andExpect(jsonPath("$.balanceAfter").value("3500.0000"));

        assertThat(api.balanceOf(alice, aliceWallet)).isEqualTo("3500.0000");
        assertThat(api.balanceOf(bob, bobWallet)).isEqualTo("1500.0000");
    }

    @Test
    void aTransferWritesOneTransactionAndTwoLedgerEntriesThatNetToZero() throws Exception {
        String alice = api.tokenFor("alice@example.com");
        String bob = api.tokenFor("bob@example.com");
        UUID aliceWallet = api.createWallet(alice, "USD");
        UUID bobWallet = api.createWallet(bob, "USD");
        api.deposit(alice, aliceWallet, "dep-1", "100.00").andExpect(status().isCreated());

        api.transfer(alice, aliceWallet, bobWallet, "tr-1", "40.00").andExpect(status().isCreated());

        assertThat(count("transactions")).isEqualTo(2);      // the deposit and the transfer
        assertThat(count("ledger_entries")).isEqualTo(3);    // one for the deposit, two for the transfer
        BigDecimal net = jdbcTemplate.queryForObject(
                "select coalesce(sum(case e.direction when 'CREDIT' then e.amount else -e.amount end), 0) "
                        + "from ledger_entries e join transactions t on t.id = e.transaction_id "
                        + "where t.type = 'TRANSFER'", BigDecimal.class);
        assertThat(net).isEqualByComparingTo("0");
    }

    @Test
    void insufficientFundsIsRefusedAndChangesNothing() throws Exception {
        String alice = api.tokenFor("alice@example.com");
        String bob = api.tokenFor("bob@example.com");
        UUID aliceWallet = api.createWallet(alice, "USD");
        UUID bobWallet = api.createWallet(bob, "USD");
        api.deposit(alice, aliceWallet, "dep-1", "100.00").andExpect(status().isCreated());

        api.transfer(alice, aliceWallet, bobWallet, "tr-1", "100.01").andExpect(status().isUnprocessableEntity());

        assertThat(api.balanceOf(alice, aliceWallet)).isEqualTo("100.0000");
        assertThat(api.balanceOf(bob, bobWallet)).isEqualTo("0.0000");
        assertThat(count("transactions")).isEqualTo(1);
        assertThat(count("ledger_entries")).isEqualTo(1);
    }

    @Test
    void transferBetweenDifferentCurrenciesIsRejected() throws Exception {
        String alice = api.tokenFor("alice@example.com");
        String bob = api.tokenFor("bob@example.com");
        UUID aliceWallet = api.createWallet(alice, "USD");
        UUID bobWallet = api.createWallet(bob, "UGX");
        api.deposit(alice, aliceWallet, "dep-1", "100.00").andExpect(status().isCreated());

        api.transfer(alice, aliceWallet, bobWallet, "tr-1", "10.00").andExpect(status().isUnprocessableEntity());

        assertThat(api.balanceOf(alice, aliceWallet)).isEqualTo("100.0000");
    }

    @Test
    void transferToTheSameWalletIsRejected() throws Exception {
        String alice = api.tokenFor("alice@example.com");
        UUID aliceWallet = api.createWallet(alice, "USD");
        api.deposit(alice, aliceWallet, "dep-1", "100.00").andExpect(status().isCreated());

        api.transfer(alice, aliceWallet, aliceWallet, "tr-1", "10.00").andExpect(status().isBadRequest());

        assertThat(api.balanceOf(alice, aliceWallet)).isEqualTo("100.0000");
    }

    @Test
    void anUnknownDestinationIsNotFound() throws Exception {
        String alice = api.tokenFor("alice@example.com");
        UUID aliceWallet = api.createWallet(alice, "USD");
        api.deposit(alice, aliceWallet, "dep-1", "100.00").andExpect(status().isCreated());

        api.transfer(alice, aliceWallet, UUID.randomUUID(), "tr-1", "10.00").andExpect(status().isNotFound());

        assertThat(api.balanceOf(alice, aliceWallet)).isEqualTo("100.0000");
    }

    @Test
    void youCannotSendMoneyOutOfSomeoneElsesWallet() throws Exception {
        String alice = api.tokenFor("alice@example.com");
        String bob = api.tokenFor("bob@example.com");
        UUID aliceWallet = api.createWallet(alice, "USD");
        UUID bobWallet = api.createWallet(bob, "USD");
        api.deposit(alice, aliceWallet, "dep-1", "100.00").andExpect(status().isCreated());

        // Bob names Alice's wallet as the source and his own as the destination.
        api.transfer(bob, aliceWallet, bobWallet, "theft", "100.00").andExpect(status().isNotFound());

        assertThat(api.balanceOf(alice, aliceWallet)).isEqualTo("100.0000");
        assertThat(api.balanceOf(bob, bobWallet)).isEqualTo("0.0000");
    }

    @Test
    void repeatingATransferWithTheSameKeyMovesMoneyOnlyOnce() throws Exception {
        String alice = api.tokenFor("alice@example.com");
        String bob = api.tokenFor("bob@example.com");
        UUID aliceWallet = api.createWallet(alice, "USD");
        UUID bobWallet = api.createWallet(bob, "USD");
        api.deposit(alice, aliceWallet, "dep-1", "100.00").andExpect(status().isCreated());

        String first = api.transfer(alice, aliceWallet, bobWallet, "retry-me", "30.00")
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        String second = api.transfer(alice, aliceWallet, bobWallet, "retry-me", "30.00")
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();

        assertThat(objectMapper.readTree(second).get("reference")).isEqualTo(objectMapper.readTree(first).get("reference"));
        assertThat(api.balanceOf(alice, aliceWallet)).isEqualTo("70.0000");
        assertThat(api.balanceOf(bob, bobWallet)).isEqualTo("30.0000");
        assertThat(count("transactions")).isEqualTo(2);
        assertThat(count("ledger_entries")).isEqualTo(3);
    }

    @Test
    void reusingAKeyForADifferentAmountIsRejected() throws Exception {
        String alice = api.tokenFor("alice@example.com");
        String bob = api.tokenFor("bob@example.com");
        UUID aliceWallet = api.createWallet(alice, "USD");
        UUID bobWallet = api.createWallet(bob, "USD");
        api.deposit(alice, aliceWallet, "dep-1", "100.00").andExpect(status().isCreated());
        api.transfer(alice, aliceWallet, bobWallet, "same-key", "30.00").andExpect(status().isCreated());

        api.transfer(alice, aliceWallet, bobWallet, "same-key", "40.00").andExpect(status().isConflict());

        assertThat(api.balanceOf(alice, aliceWallet)).isEqualTo("70.0000");
    }

    @Test
    void theIdempotencyKeyHeaderIsRequired() throws Exception {
        String alice = api.tokenFor("alice@example.com");
        String bob = api.tokenFor("bob@example.com");
        UUID aliceWallet = api.createWallet(alice, "USD");
        UUID bobWallet = api.createWallet(bob, "USD");
        api.deposit(alice, aliceWallet, "dep-1", "100.00").andExpect(status().isCreated());

        api.transfer(alice, aliceWallet, bobWallet, null, "10.00").andExpect(status().isBadRequest());

        assertThat(count("transactions")).isEqualTo(1);
    }

    @Test
    void aFrozenDestinationRefusesTheTransferAndTheSenderKeepsTheirMoney() throws Exception {
        String alice = api.tokenFor("alice@example.com");
        String bob = api.tokenFor("bob@example.com");
        UUID aliceWallet = api.createWallet(alice, "USD");
        UUID bobWallet = api.createWallet(bob, "USD");
        api.deposit(alice, aliceWallet, "dep-1", "100.00").andExpect(status().isCreated());
        freeze(bobWallet);

        api.transfer(alice, aliceWallet, bobWallet, "tr-1", "50.00").andExpect(status().isConflict());

        // The debit had already been applied in memory when the credit failed; the rollback must undo it.
        assertThat(api.balanceOf(alice, aliceWallet)).isEqualTo("100.0000");
        assertThat(count("transactions")).isEqualTo(1);
        assertThat(count("ledger_entries")).isEqualTo(1);
    }

    @Test
    void aFrozenSourceCannotSend() throws Exception {
        String alice = api.tokenFor("alice@example.com");
        String bob = api.tokenFor("bob@example.com");
        UUID aliceWallet = api.createWallet(alice, "USD");
        UUID bobWallet = api.createWallet(bob, "USD");
        api.deposit(alice, aliceWallet, "dep-1", "100.00").andExpect(status().isCreated());
        freeze(aliceWallet);

        api.transfer(alice, aliceWallet, bobWallet, "tr-1", "50.00").andExpect(status().isConflict());

        assertThat(api.balanceOf(bob, bobWallet)).isEqualTo("0.0000");
    }

    @Test
    void transfersRequireAuthentication() throws Exception {
        mvc.perform(post("/api/v1/wallets/" + UUID.randomUUID() + "/transfers")
                        .header("Idempotency-Key", "no-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"destinationWalletId\":\"" + UUID.randomUUID() + "\",\"amount\":\"10\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void aDestinationThatIsNotAUuidIsABadRequest() throws Exception {
        String alice = api.tokenFor("alice@example.com");
        UUID aliceWallet = api.createWallet(alice, "USD");

        api.transfer(alice, aliceWallet, "not-a-uuid", "tr-1", "10.00").andExpect(status().isBadRequest());
    }

    @ParameterizedTest
    @CsvSource({
            "0",
            "-5",
            "10.5"
    })
    void invalidAmountsAreRejected(String amount) throws Exception {
        String alice = api.tokenFor("alice@example.com");
        String bob = api.tokenFor("bob@example.com");
        UUID aliceWallet = api.createWallet(alice, "UGX");   // UGX allows no decimals, so 10.5 is invalid
        UUID bobWallet = api.createWallet(bob, "UGX");

        api.transfer(alice, aliceWallet, bobWallet, "bad-amount", amount).andExpect(status().isBadRequest());

        assertThat(count("ledger_entries")).isZero();
    }

    @Test
    void totalMoneyIsConservedAcrossManyTransfers() throws Exception {
        String alice = api.tokenFor("alice@example.com");
        String bob = api.tokenFor("bob@example.com");
        UUID aliceWallet = api.createWallet(alice, "USD");
        UUID bobWallet = api.createWallet(bob, "USD");
        api.deposit(alice, aliceWallet, "seed-a", "1000.00").andExpect(status().isCreated());
        api.deposit(bob, bobWallet, "seed-b", "500.00").andExpect(status().isCreated());

        api.transfer(alice, aliceWallet, bobWallet, "t1", "100.00").andExpect(status().isCreated());
        api.transfer(bob, bobWallet, aliceWallet, "t2", "250.50").andExpect(status().isCreated());
        api.transfer(alice, aliceWallet, bobWallet, "t3", "0.01").andExpect(status().isCreated());
        api.transfer(bob, bobWallet, aliceWallet, "t4", "99.99").andExpect(status().isCreated());

        BigDecimal total = jdbcTemplate.queryForObject("select sum(balance) from wallets", BigDecimal.class);
        assertThat(total).isEqualByComparingTo("1500.00");
        assertThat(api.balanceOf(alice, aliceWallet)).isEqualTo("1250.4800");
        assertThat(api.balanceOf(bob, bobWallet)).isEqualTo("249.5200");
    }
}
