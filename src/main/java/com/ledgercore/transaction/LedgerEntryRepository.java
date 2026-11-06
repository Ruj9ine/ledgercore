package com.ledgercore.transaction;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface LedgerEntryRepository extends JpaRepository<LedgerEntry, UUID> {

    List<LedgerEntry> findByWalletIdOrderByCreatedAtAsc(UUID walletId);
    List<LedgerEntry> findByTransactionId(UUID transactionId);
}
