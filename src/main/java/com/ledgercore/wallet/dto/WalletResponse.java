package com.ledgercore.wallet.dto;

import com.ledgercore.wallet.SupportedCurrency;
import com.ledgercore.wallet.Wallet;
import com.ledgercore.wallet.WalletStatus;

import java.time.Instant;
import java.util.UUID;

public record WalletResponse(UUID id,
                             SupportedCurrency currency,
                             String balance,
                             WalletStatus status,
                             Instant createdAt) {

    public static WalletResponse from(Wallet wallet) {
        // The balance is sent as a string: many JSON clients parse numbers as floating point and lose precision.
        return new WalletResponse(wallet.getId(), wallet.getCurrency(),
                wallet.getBalance().toPlainString(), wallet.getStatus(), wallet.getCreatedAt());
    }
}
