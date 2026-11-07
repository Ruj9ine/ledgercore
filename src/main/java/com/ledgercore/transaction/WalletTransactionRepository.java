package com.ledgercore.transaction;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface WalletTransactionRepository extends JpaRepository<WalletTransaction, UUID> {

    Optional<WalletTransaction> findByUserIdAndIdempotencyKey(UUID userId, String idempotencyKey);
    Optional<WalletTransaction> findByReference(String reference);
}