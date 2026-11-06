package com.ledgercore.wallet;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class SupportedCurrencyTest {

    @ParameterizedTest
    @CsvSource({
            "UGX, 100,     true",
            "UGX, 100.00,  true",
            "UGX, 10.5,    false",
            "USD, 10,      true",
            "USD, 10.50,   true",
            "USD, 10.505,  false"
    })
    void acceptsScaleOfChecksDecimalPlaces(SupportedCurrency currency, String amount, boolean expected) {
        assertThat(currency.acceptsScaleOf(new BigDecimal(amount))).isEqualTo(expected);
    }
}