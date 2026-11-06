package com.ledgercore.transaction.dto;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.util.UUID;

public record TransferRequest(
        @NotNull UUID destinationWalletId,
        @NotNull @Positive @Digits(integer = 10, fraction = 4) BigDecimal amount) {
}
