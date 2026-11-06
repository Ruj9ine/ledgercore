package com.ledgercore.wallet;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import com.ledgercore.common.InsufficientFundsException;
import com.ledgercore.common.InvalidAmountException;
import com.ledgercore.common.WalletFrozenException;

@Entity
@Table(name = "wallets")
public class Wallet {

    public static final BigDecimal MAX_TRANSACTION_AMOUNT = new BigDecimal("1000000000");

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 3, updatable = false)
    private SupportedCurrency currency;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal balance = BigDecimal.ZERO.setScale(4);

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private WalletStatus status = WalletStatus.ACTIVE;

    @Version
    private long version;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Wallet() {
        // required by JPA
    }

    public Wallet(UUID userId, SupportedCurrency currency) {
        this.userId = userId;
        this.currency = currency;
    }

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        this.updatedAt = Instant.now();
    }

        public void credit(BigDecimal amount) {
        validateForMovement(amount);
        this.balance = this.balance.add(amount);
    }

    public void debit(BigDecimal amount) {
        validateForMovement(amount);
        if (balance.compareTo(amount) < 0) {
            throw new InsufficientFundsException();
        }
        this.balance = this.balance.subtract(amount);
    }

    public void freeze() {
        this.status = WalletStatus.FROZEN;
    }

    public void activate() {
        this.status = WalletStatus.ACTIVE;
    }

    private void validateForMovement(BigDecimal amount) {
        if (status != WalletStatus.ACTIVE) {
            throw new WalletFrozenException();
        }
        if (amount == null || amount.signum() <= 0) {
            throw new InvalidAmountException("Amount must be greater than zero.");
        }
        if (!currency.acceptsScaleOf(amount)) {
            throw new InvalidAmountException("Amount has too many decimal places for " + currency + ".");
        }
        if (amount.compareTo(MAX_TRANSACTION_AMOUNT) > 0) {
            throw new InvalidAmountException("Amount exceeds the maximum of " + MAX_TRANSACTION_AMOUNT.toPlainString() + ".");
        }
    }

    public UUID getId() { return id; }
    public UUID getUserId() { return userId; }
    public SupportedCurrency getCurrency() { return currency; }
    public BigDecimal getBalance() { return balance; }
    public WalletStatus getStatus() { return status; }
    public long getVersion() { return version; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}