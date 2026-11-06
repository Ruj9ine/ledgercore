package com.ledgercore.transaction;

import com.ledgercore.wallet.SupportedCurrency;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "transactions")
public class WalletTransaction {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, updatable = false, length = 20)
    private String reference;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(name = "source_wallet_id", updatable = false)
    private UUID sourceWalletId;

    @Column(name = "destination_wallet_id", updatable = false)
    private UUID destinationWalletId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false, length = 20)
    private TransactionType type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TransactionStatus status;

    @Column(nullable = false, updatable = false, precision = 19, scale = 4)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false, length = 3)
    private SupportedCurrency currency;

    @Column(name = "idempotency_key", nullable = false, updatable = false, length = 100)
    private String idempotencyKey;

    @Column(name = "request_hash", nullable = false, updatable = false, length = 64)
    private String requestHash;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected WalletTransaction() {
        // required by JPA
    }

    private WalletTransaction(UUID userId, UUID sourceWalletId, UUID destinationWalletId, TransactionType type,
                              SupportedCurrency currency, BigDecimal amount, String idempotencyKey, String requestHash) {
        this.reference = TransactionReference.generate();
        this.userId = userId;
        this.sourceWalletId = sourceWalletId;
        this.destinationWalletId = destinationWalletId;
        this.type = type;
        // A transaction is only saved after its ledger entries succeed, inside one database transaction,
        // so anything that fails is rolled back and never stored half-finished.
        this.status = TransactionStatus.COMPLETED;
        this.currency = currency;
        this.amount = amount;
        this.idempotencyKey = idempotencyKey;
        this.requestHash = requestHash;
    }

    public static WalletTransaction deposit(UUID userId, UUID walletId, SupportedCurrency currency,
                                            BigDecimal amount, String idempotencyKey, String requestHash) {
        return new WalletTransaction(userId, null, walletId, TransactionType.DEPOSIT,
                currency, amount, idempotencyKey, requestHash);
    }

    public static WalletTransaction withdrawal(UUID userId, UUID walletId, SupportedCurrency currency,
                                               BigDecimal amount, String idempotencyKey, String requestHash) {
        return new WalletTransaction(userId, walletId, null, TransactionType.WITHDRAWAL,
                currency, amount, idempotencyKey, requestHash);
    }

    @PrePersist
    void onCreate() {
        this.createdAt = Instant.now();
    }

    public UUID getId() { return id; }
    public String getReference() { return reference; }
    public UUID getUserId() { return userId; }
    public UUID getSourceWalletId() { return sourceWalletId; }
    public UUID getDestinationWalletId() { return destinationWalletId; }
    public TransactionType getType() { return type; }
    public TransactionStatus getStatus() { return status; }
    public BigDecimal getAmount() { return amount; }
    public SupportedCurrency getCurrency() { return currency; }
    public String getIdempotencyKey() { return idempotencyKey; }
    public String getRequestHash() { return requestHash; }
    public Instant getCreatedAt() { return createdAt; }
}
