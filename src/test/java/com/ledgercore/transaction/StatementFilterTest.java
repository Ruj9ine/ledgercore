package com.ledgercore.transaction;

import com.ledgercore.common.InvalidQueryException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class StatementFilterTest {

    private static final Instant EARLIER = Instant.parse("2026-11-01T00:00:00Z");
    private static final Instant LATER = Instant.parse("2026-11-02T00:00:00Z");

    @Test
    void defaultsAreAppliedWhenNothingIsGiven() {
        StatementFilter filter = StatementFilter.of(null, null, null, null, null, null, null);

        assertThat(filter.page()).isZero();
        assertThat(filter.size()).isEqualTo(StatementFilter.DEFAULT_SIZE);
        assertThat(filter.type()).isNull();
        assertThat(filter.reference()).isNull();
    }

    @Test
    void validValuesAreKept() {
        StatementFilter filter = StatementFilter.of(TransactionType.TRANSFER, LedgerDirection.DEBIT,
                EARLIER, LATER, "TXN-ABC", 2, 50);

        assertThat(filter.type()).isEqualTo(TransactionType.TRANSFER);
        assertThat(filter.direction()).isEqualTo(LedgerDirection.DEBIT);
        assertThat(filter.from()).isEqualTo(EARLIER);
        assertThat(filter.to()).isEqualTo(LATER);
        assertThat(filter.reference()).isEqualTo("TXN-ABC");
        assertThat(filter.page()).isEqualTo(2);
        assertThat(filter.size()).isEqualTo(50);
    }

    @ParameterizedTest
    @CsvSource({
            "-1, 20",
            "0, 0",
            "0, 101",
            "0, -5"
    })
    void invalidPageOrSizeIsRejected(int page, int size) {
        assertThatThrownBy(() -> StatementFilter.of(null, null, null, null, null, page, size))
                .isInstanceOf(InvalidQueryException.class);
    }

    @Test
    void fromMustBeEarlierThanTo() {
        assertThatThrownBy(() -> StatementFilter.of(null, null, LATER, EARLIER, null, null, null))
                .isInstanceOf(InvalidQueryException.class);
        assertThatThrownBy(() -> StatementFilter.of(null, null, EARLIER, EARLIER, null, null, null))
                .isInstanceOf(InvalidQueryException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"TXN%", "TXN_1", "a b", "TXN-ABCDEFGHIJKLMNOPQRSTUVWXYZ"})
    void referencesWithOtherCharactersOrTooLongAreRejected(String reference) {
        assertThatThrownBy(() -> StatementFilter.of(null, null, null, null, reference, null, null))
                .isInstanceOf(InvalidQueryException.class);
    }

    @Test
    void referenceIsTrimmedAndUppercasedAndBlankMeansNoFilter() {
        assertThat(StatementFilter.of(null, null, null, null, "  txn-7k  ", null, null).reference())
                .isEqualTo("TXN-7K");
        assertThat(StatementFilter.of(null, null, null, null, "   ", null, null).reference()).isNull();
    }
}
