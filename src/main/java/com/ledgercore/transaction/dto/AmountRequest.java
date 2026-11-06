package com.ledgercore.transaction.dto;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;

public record AmountRequest(
        // The Digits bound also stops absurd inputs such as 1E+999999 before any arithmetic happens.
        @NotNull @Positive @Digits(integer = 10, fraction = 4) BigDecimal amount) {
}