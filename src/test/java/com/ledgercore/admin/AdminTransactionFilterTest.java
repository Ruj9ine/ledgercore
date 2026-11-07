package com.ledgercore.admin;

import com.ledgercore.common.InvalidQueryException;
import com.ledgercore.transaction.TransactionType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AdminTransactionFilterTest {

    private static final Instant EARLIER = Instant.parse("2026-11-01T00:00:00Z");
    private static final Instant LATER = Instant.parse("2026-11-02T00:00:00Z");

    @Test
    void defaultsAreAppliedWhenNothingIsGiven() {
        AdminTransactionFilter filter = AdminTransactionFilter.of(null, null, null, null, null, null, null, null);

        assertThat(filter.type()).isNull();
        assertThat(filter.reference()).isNull();
        assertThat(filter.page()).isZero();
        assertThat(filter.size()).isEqualTo(20);
    }

    @Test
    void validValuesAreKept() {
        UUID walletId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        AdminTransactionFilter filter = AdminTransactionFilter.of(TransactionType.TRANSFER, walletId, userId,
                EARLIER, LATER, "TXN-ABC", 2, 50);

        assertThat(filter.type()).isEqualTo(TransactionType.TRANSFER);
        assertThat(filter.walletId()).isEqualTo(walletId);
        assertThat(filter.userId()).isEqualTo(userId);
        assertThat(filter.from()).isEqualTo(EARLIER);
        assertThat(filter.to()).isEqualTo(LATER);
        assertThat(filter.reference()).isEqualTo("TXN-ABC");
        assertThat(filter.page()).isEqualTo(2);
        assertThat(filter.size()).isEqualTo(50);
    }

    @ParameterizedTest
    @ValueSource(strings = {"TXN%", "TXN_1", "a b", "TXN-ABCDEFGHIJKLMNOPQRSTUVWXYZ"})
    void referencesWithOtherCharactersOrTooLongAreRejected(String reference) {
        assertThatThrownBy(() -> AdminTransactionFilter.of(null, null, null, null, null, reference, null, null))
                .isInstanceOf(InvalidQueryException.class);
    }

    @Test
    void referenceIsTrimmedAndUppercasedAndBlankMeansNoFilter() {
        assertThat(AdminTransactionFilter.of(null, null, null, null, null, "  txn-7k  ", null, null).reference())
                .isEqualTo("TXN-7K");
        assertThat(AdminTransactionFilter.of(null, null, null, null, null, "   ", null, null).reference()).isNull();
    }

    @Test
    void fromMustBeEarlierThanTo() {
        assertThatThrownBy(() -> AdminTransactionFilter.of(null, null, null, LATER, EARLIER, null, null, null))
                .isInstanceOf(InvalidQueryException.class);
        assertThatThrownBy(() -> AdminTransactionFilter.of(null, null, null, EARLIER, EARLIER, null, null, null))
                .isInstanceOf(InvalidQueryException.class);
    }

    @Test
    void invalidPagingIsRejected() {
        assertThatThrownBy(() -> AdminTransactionFilter.of(null, null, null, null, null, null, 0, 101))
                .isInstanceOf(InvalidQueryException.class);
    }
}