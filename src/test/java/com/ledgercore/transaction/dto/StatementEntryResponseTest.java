package com.ledgercore.transaction.dto;

import com.ledgercore.transaction.LedgerEntry;
import com.ledgercore.transaction.WalletTransaction;
import com.ledgercore.wallet.SupportedCurrency;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class StatementEntryResponseTest {

    private final UUID userId = UUID.randomUUID();
    private final UUID aliceWallet = UUID.randomUUID();
    private final UUID bobWallet = UUID.randomUUID();

    // The link to the transaction is normally loaded by Hibernate, so these tests set it directly.
    private LedgerEntry withTransaction(LedgerEntry entry, WalletTransaction transaction) {
        ReflectionTestUtils.setField(entry, "transaction", transaction);
        return entry;
    }

    @Test
    void theSendersEntryPointsAtTheRecipient() {
        WalletTransaction transfer = WalletTransaction.transfer(userId, aliceWallet, bobWallet,
                SupportedCurrency.USD, new BigDecimal("30.0000"), "k", "h");
        LedgerEntry debit = withTransaction(LedgerEntry.debit(null, aliceWallet,
                new BigDecimal("30.0000"), new BigDecimal("70.0000")), transfer);

        StatementEntryResponse response = StatementEntryResponse.from(debit);

        assertThat(response.counterpartyWalletId()).isEqualTo(bobWallet);
        assertThat(response.reference()).isEqualTo(transfer.getReference());
        assertThat(response.amount()).isEqualTo("30.0000");
        assertThat(response.balanceAfter()).isEqualTo("70.0000");
    }

    @Test
    void theRecipientsEntryPointsAtTheSender() {
        WalletTransaction transfer = WalletTransaction.transfer(userId, aliceWallet, bobWallet,
                SupportedCurrency.USD, new BigDecimal("30.0000"), "k", "h");
        LedgerEntry credit = withTransaction(LedgerEntry.credit(null, bobWallet,
                new BigDecimal("30.0000"), new BigDecimal("30.0000")), transfer);

        assertThat(StatementEntryResponse.from(credit).counterpartyWalletId()).isEqualTo(aliceWallet);
    }

    @Test
    void depositsAndWithdrawalsHaveNoCounterparty() {
        WalletTransaction deposit = WalletTransaction.deposit(userId, aliceWallet, SupportedCurrency.USD,
                new BigDecimal("10.0000"), "k", "h");
        LedgerEntry credit = withTransaction(LedgerEntry.credit(null, aliceWallet,
                new BigDecimal("10.0000"), new BigDecimal("10.0000")), deposit);

        assertThat(StatementEntryResponse.from(credit).counterpartyWalletId()).isNull();
    }
}