package com.ledgercore.transaction;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;
import java.util.UUID;

public interface LedgerEntryRepository extends JpaRepository<LedgerEntry, UUID>, JpaSpecificationExecutor<LedgerEntry> {

    List<LedgerEntry> findByWalletIdOrderByCreatedAtAsc(UUID walletId);

    List<LedgerEntry> findByTransactionId(UUID transactionId);

    /** Loads each entry's transaction in the same query, which avoids one extra query per row on a page. */
    @Override
    @EntityGraph(attributePaths = "transaction")
    Page<LedgerEntry> findAll(Specification<LedgerEntry> spec, Pageable pageable);
}
