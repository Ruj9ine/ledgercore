package com.ledgercore.transaction;

import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Builds the database query for a wallet statement, adding a condition only for each filter that was supplied. */
final class StatementSpecifications {

    private StatementSpecifications() {
    }

    static Specification<LedgerEntry> forWallet(UUID walletId, StatementFilter filter) {
        return (root, query, cb) -> {
            Join<LedgerEntry, WalletTransaction> transaction = root.join("transaction");
            List<Predicate> conditions = new ArrayList<>();

            // Always: this wallet's entries and nobody else's.
            conditions.add(cb.equal(root.get("walletId"), walletId));

            if (filter.type() != null) {
                conditions.add(cb.equal(transaction.get("type"), filter.type()));
            }
            if (filter.direction() != null) {
                conditions.add(cb.equal(root.get("direction"), filter.direction()));
            }
            if (filter.from() != null) {
                conditions.add(cb.greaterThanOrEqualTo(root.<Instant>get("createdAt"), filter.from()));
            }
            if (filter.to() != null) {
                conditions.add(cb.lessThan(root.<Instant>get("createdAt"), filter.to()));
            }
            if (filter.reference() != null) {
                // Safe without escaping: StatementFilter only lets letters, digits and hyphens through.
                conditions.add(cb.like(transaction.<String>get("reference"), "%" + filter.reference() + "%"));
            }
            return cb.and(conditions.toArray(Predicate[]::new));
        };
    }
}
