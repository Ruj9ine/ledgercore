package com.ledgercore.transaction.dto;

import com.ledgercore.transaction.TransactionStatus;
import com.ledgercore.transaction.TransactionType;
import com.ledgercore.transaction.WalletTransaction;
import com.ledgercore.wallet.SupportedCurrency;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record TransactionResponse(String reference,
                                  TransactionType type,
                                  TransactionStatus status,
                                  String amount,
                                  SupportedCurrency currency,
                                  UUID walletId,
                                  String balanceAfter,
                                  Instant createdAt) {

    public static TransactionResponse of(WalletTransaction transaction, UUID walletId, BigDecimal balanceAfter) {
        return new TransactionResponse(transaction.getReference(), transaction.getType(), transaction.getStatus(),
                transaction.getAmount().toPlainString(), transaction.getCurrency(), walletId,
                balanceAfter.toPlainString(), transaction.getCreatedAt());
    }
}