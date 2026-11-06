package com.ledgercore.transaction;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class RequestFingerprintTest {

    private final UUID walletId = UUID.randomUUID();

    @Test
    void numericallyEqualAmountsGiveTheSameFingerprint() {
        assertThat(RequestFingerprint.of(TransactionType.DEPOSIT, walletId, new BigDecimal("100")))
                .isEqualTo(RequestFingerprint.of(TransactionType.DEPOSIT, walletId, new BigDecimal("100.00")));
    }

    @Test
    void differentAmountWalletOrTypeGivesADifferentFingerprint() {
        String base = RequestFingerprint.of(TransactionType.DEPOSIT, walletId, new BigDecimal("100"));

        assertThat(RequestFingerprint.of(TransactionType.DEPOSIT, walletId, new BigDecimal("101"))).isNotEqualTo(base);
        assertThat(RequestFingerprint.of(TransactionType.DEPOSIT, UUID.randomUUID(), new BigDecimal("100"))).isNotEqualTo(base);
        assertThat(RequestFingerprint.of(TransactionType.WITHDRAWAL, walletId, new BigDecimal("100"))).isNotEqualTo(base);
    }

    @Test
    void fingerprintIs64HexCharacters() {
        assertThat(RequestFingerprint.of(TransactionType.DEPOSIT, walletId, BigDecimal.TEN)).matches("[0-9a-f]{64}");
    }
}
