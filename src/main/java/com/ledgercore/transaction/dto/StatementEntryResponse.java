package com.ledgercore.transaction.dto;

import com.ledgercore.transaction.LedgerDirection;
import com.ledgercore.transaction.LedgerEntry;
import com.ledgercore.transaction.TransactionType;
import com.ledgercore.transaction.WalletTransaction;

import java.time.Instant;
import java.util.UUID;

/** One line of a wallet statement: what happened to this wallet, and what its balance was afterwards. */
public record StatementEntryResponse(String reference,
                                     TransactionType type,
                                     LedgerDirection direction,
                                     String amount,
                                     String balanceAfter,
                                     UUID counterpartyWalletId,
                                     Instant createdAt) {

    public static StatementEntryResponse from(LedgerEntry entry) {
        WalletTransaction transaction = entry.getTransaction();
        // For a transfer, the counterparty is the wallet on the other side: where the money went, or where it came from.
        UUID counterparty = null;
        if (transaction.getType() == TransactionType.TRANSFER) {
            counterparty = entry.getDirection() == LedgerDirection.DEBIT
                    ? transaction.getDestinationWalletId()
                    : transaction.getSourceWalletId();
        }
        return new StatementEntryResponse(transaction.getReference(), transaction.getType(), entry.getDirection(),
                entry.getAmount().toPlainString(), entry.getBalanceAfter().toPlainString(),
                counterparty, entry.getCreatedAt());
    }
}
