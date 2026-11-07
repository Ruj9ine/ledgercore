package com.ledgercore.common;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PageParamsTest {

    @Test
    void defaultsApplyWhenNothingIsGiven() {
        PageParams params = PageParams.of(null, null);

        assertThat(params.page()).isZero();
        assertThat(params.size()).isEqualTo(PageParams.DEFAULT_SIZE);
    }

    @Test
    void validValuesAreKept() {
        PageParams params = PageParams.of(3, 100);

        assertThat(params.page()).isEqualTo(3);
        assertThat(params.size()).isEqualTo(100);
    }

    @ParameterizedTest
    @CsvSource({
            "-1, 20",
            "0, 0",
            "0, 101",
            "0, -5"
    })
    void invalidPageOrSizeIsRejected(int page, int size) {
        assertThatThrownBy(() -> PageParams.of(page, size)).isInstanceOf(InvalidQueryException.class);
    }
}
