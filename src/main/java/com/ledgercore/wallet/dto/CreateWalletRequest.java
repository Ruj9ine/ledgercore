package com.ledgercore.wallet.dto;

import com.ledgercore.wallet.SupportedCurrency;
import jakarta.validation.constraints.NotNull;

public record CreateWalletRequest(@NotNull SupportedCurrency currency) {
}
