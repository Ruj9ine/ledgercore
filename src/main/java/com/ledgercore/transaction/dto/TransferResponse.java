package com.ledgercore.transaction.dto;

import com.ledgercore.transaction.TransactionStatus;
import com.ledgercore.transaction.TransactionType;
import com.ledgercore.transaction.WalletTransaction;
import com.ledgercore.wallet.SupportedCurrency;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** The sender's receipt. It deliberately shows the sender's balance only, never the recipient's. */
public record TransferResponse(String reference,
                               TransactionType type,
                               TransactionStatus status,
                               String amount,
                               SupportedCurrency currency,
                               UUID sourceWalletId,
                               UUID destinationWalletId,
                               String balanceAfter,
                               Instant createdAt) {

    public static TransferResponse of(WalletTransaction transaction, BigDecimal senderBalanceAfter) {
        return new TransferResponse(transaction.getReference(), transaction.getType(), transaction.getStatus(),
                transaction.getAmount().toPlainString(), transaction.getCurrency(),
                transaction.getSourceWalletId(), transaction.getDestinationWalletId(),
                senderBalanceAfter.toPlainString(), transaction.getCreatedAt());
    }
}
