package com.ledgercore.transaction.dto;

import com.ledgercore.transaction.TransactionStatus;
import com.ledgercore.transaction.TransactionType;
import com.ledgercore.transaction.WalletTransaction;
import com.ledgercore.wallet.SupportedCurrency;

import java.time.Instant;
import java.util.UUID;

/** A transaction as seen by either party to it. Balances are deliberately not included. */
public record TransactionDetailResponse(String reference,
                                        TransactionType type,
                                        TransactionStatus status,
                                        String amount,
                                        SupportedCurrency currency,
                                        UUID sourceWalletId,
                                        UUID destinationWalletId,
                                        Instant createdAt) {

    public static TransactionDetailResponse from(WalletTransaction transaction) {
        return new TransactionDetailResponse(transaction.getReference(), transaction.getType(), transaction.getStatus(),
                transaction.getAmount().toPlainString(), transaction.getCurrency(),
                transaction.getSourceWalletId(), transaction.getDestinationWalletId(), transaction.getCreatedAt());
    }
}
