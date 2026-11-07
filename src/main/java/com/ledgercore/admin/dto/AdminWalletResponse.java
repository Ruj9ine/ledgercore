package com.ledgercore.admin.dto;

import com.ledgercore.wallet.SupportedCurrency;
import com.ledgercore.wallet.Wallet;
import com.ledgercore.wallet.WalletStatus;

import java.time.Instant;
import java.util.UUID;

public record AdminWalletResponse(UUID id,
                                  UUID userId,
                                  SupportedCurrency currency,
                                  String balance,
                                  WalletStatus status,
                                  Instant createdAt,
                                  Instant updatedAt) {

    public static AdminWalletResponse from(Wallet wallet) {
        return new AdminWalletResponse(wallet.getId(), wallet.getUserId(), wallet.getCurrency(),
                wallet.getBalance().toPlainString(), wallet.getStatus(), wallet.getCreatedAt(), wallet.getUpdatedAt());
    }
}
