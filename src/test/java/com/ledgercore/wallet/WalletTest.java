package com.ledgercore.wallet;

import com.ledgercore.common.InsufficientFundsException;
import com.ledgercore.common.InvalidAmountException;
import com.ledgercore.common.WalletFrozenException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class WalletTest {

    private static Wallet walletIn(SupportedCurrency currency) {
        return new Wallet(UUID.randomUUID(), currency);
    }

    @Test
    void creditIncreasesTheBalance() {
        Wallet wallet = walletIn(SupportedCurrency.USD);

        wallet.credit(new BigDecimal("100.50"));

        assertThat(wallet.getBalance()).isEqualByComparingTo("100.50");
    }

    @Test
    void debitDecreasesTheBalance() {
        Wallet wallet = walletIn(SupportedCurrency.USD);
        wallet.credit(new BigDecimal("100.00"));

        wallet.debit(new BigDecimal("40.25"));

        assertThat(wallet.getBalance()).isEqualByComparingTo("59.75");
    }

    @Test
    void debitOfTheExactBalanceLeavesZero() {
        Wallet wallet = walletIn(SupportedCurrency.USD);
        wallet.credit(new BigDecimal("100.00"));

        wallet.debit(new BigDecimal("100.00"));

        assertThat(wallet.getBalance()).isEqualByComparingTo("0");
    }

    @Test
    void debitBeyondTheBalanceIsRefusedAndChangesNothing() {
        Wallet wallet = walletIn(SupportedCurrency.USD);
        wallet.credit(new BigDecimal("100.00"));

        assertThatThrownBy(() -> wallet.debit(new BigDecimal("100.01")))
                .isInstanceOf(InsufficientFundsException.class);

        assertThat(wallet.getBalance()).isEqualByComparingTo("100.00");
    }

    @Test
    void zeroAmountIsRejected() {
        Wallet wallet = walletIn(SupportedCurrency.USD);

        assertThatThrownBy(() -> wallet.credit(BigDecimal.ZERO))
                .isInstanceOf(InvalidAmountException.class);
    }

    @Test
    void negativeAmountIsRejected() {
        Wallet wallet = walletIn(SupportedCurrency.USD);

        assertThatThrownBy(() -> wallet.credit(new BigDecimal("-5")))
                .isInstanceOf(InvalidAmountException.class);
    }

    @Test
    void amountWithTooManyDecimalPlacesForTheCurrencyIsRejected() {
        Wallet wallet = walletIn(SupportedCurrency.UGX);

        assertThatThrownBy(() -> wallet.credit(new BigDecimal("10.5")))
                .isInstanceOf(InvalidAmountException.class);
        assertThat(wallet.getBalance()).isEqualByComparingTo("0");
    }

    @Test
    void amountAboveTheMaximumIsRejected() {
        Wallet wallet = walletIn(SupportedCurrency.USD);

        assertThatThrownBy(() -> wallet.credit(new BigDecimal("1000000000.01")))
                .isInstanceOf(InvalidAmountException.class);
    }

    @Test
    void frozenWalletRejectsBothCreditAndDebit() {
        Wallet wallet = walletIn(SupportedCurrency.USD);
        wallet.credit(new BigDecimal("50"));
        wallet.freeze();

        assertThatThrownBy(() -> wallet.credit(BigDecimal.TEN)).isInstanceOf(WalletFrozenException.class);
        assertThatThrownBy(() -> wallet.debit(BigDecimal.TEN)).isInstanceOf(WalletFrozenException.class);
        assertThat(wallet.getBalance()).isEqualByComparingTo("50");
    }
}
