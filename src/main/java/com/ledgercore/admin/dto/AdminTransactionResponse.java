package com.ledgercore.admin.dto;

import com.ledgercore.transaction.TransactionStatus;
import com.ledgercore.transaction.TransactionType;
import com.ledgercore.transaction.WalletTransaction;
import com.ledgercore.wallet.SupportedCurrency;

import java.time.Instant;
import java.util.UUID;

public record AdminTransactionResponse(String reference,
                                       TransactionType type,
                                       TransactionStatus status,
                                       String amount,
                                       SupportedCurrency currency,
                                       UUID userId,
                                       UUID sourceWalletId,
                                       UUID destinationWalletId,
                                       Instant createdAt) {

    public static AdminTransactionResponse from(WalletTransaction transaction) {
        return new AdminTransactionResponse(transaction.getReference(), transaction.getType(), transaction.getStatus(),
                transaction.getAmount().toPlainString(), transaction.getCurrency(), transaction.getUserId(),
                transaction.getSourceWalletId(), transaction.getDestinationWalletId(), transaction.getCreatedAt());
    }
}