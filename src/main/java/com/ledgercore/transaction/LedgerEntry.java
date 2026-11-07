package com.ledgercore.transaction;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import org.hibernate.annotations.Immutable;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** One immutable line in a wallet's history. Rows are only ever inserted, never updated or deleted. */
@Entity
@Immutable
@Table(name = "ledger_entries")
public class LedgerEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "transaction_id", nullable = false)
    private UUID transactionId;

        // A read-only link to the owning transaction, so statements can filter on and show its type and reference.
    // It maps the same column as transactionId, so it must never be written through.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "transaction_id", insertable = false, updatable = false)
    private WalletTransaction transaction;

    @Column(name = "wallet_id", nullable = false)
    private UUID walletId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 6)
    private LedgerDirection direction;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal amount;

    @Column(name = "balance_after", nullable = false, precision = 19, scale = 4)
    private BigDecimal balanceAfter;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected LedgerEntry() {
        // required by JPA
    }

    private LedgerEntry(UUID transactionId, UUID walletId, LedgerDirection direction,
                        BigDecimal amount, BigDecimal balanceAfter) {
        this.transactionId = transactionId;
        this.walletId = walletId;
        this.direction = direction;
        this.amount = amount;
        this.balanceAfter = balanceAfter;
    }

    public static LedgerEntry credit(UUID transactionId, UUID walletId, BigDecimal amount, BigDecimal balanceAfter) {
        return new LedgerEntry(transactionId, walletId, LedgerDirection.CREDIT, amount, balanceAfter);
    }

    public static LedgerEntry debit(UUID transactionId, UUID walletId, BigDecimal amount, BigDecimal balanceAfter) {
        return new LedgerEntry(transactionId, walletId, LedgerDirection.DEBIT, amount, balanceAfter);
    }

    public WalletTransaction getTransaction() {
         return transaction; 
    }

    @PrePersist
    void onCreate() {
        this.createdAt = Instant.now();
    }

    public UUID getId() { return id; }
    public UUID getTransactionId() { return transactionId; }
    public UUID getWalletId() { return walletId; }
    public LedgerDirection getDirection() { return direction; }
    public BigDecimal getAmount() { return amount; }
    public BigDecimal getBalanceAfter() { return balanceAfter; }
    public Instant getCreatedAt() { return createdAt; }
}
