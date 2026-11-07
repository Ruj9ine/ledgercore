package com.ledgercore.admin;

import com.ledgercore.common.LikePatterns;
import com.ledgercore.transaction.WalletTransaction;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

final class TransactionSpecifications {

    private TransactionSpecifications() {
    }

    static Specification<WalletTransaction> matching(AdminTransactionFilter filter) {
        return (root, query, cb) -> {
            List<Predicate> conditions = new ArrayList<>();
            if (filter.type() != null) {
                conditions.add(cb.equal(root.get("type"), filter.type()));
            }
            if (filter.walletId() != null) {
                // "Touching this wallet" means it is on either side of the transaction.
                conditions.add(cb.or(
                        cb.equal(root.get("sourceWalletId"), filter.walletId()),
                        cb.equal(root.get("destinationWalletId"), filter.walletId())));
            }
            if (filter.userId() != null) {
                conditions.add(cb.equal(root.get("userId"), filter.userId()));
            }
            if (filter.from() != null) {
                conditions.add(cb.greaterThanOrEqualTo(root.<Instant>get("createdAt"), filter.from()));
            }
            if (filter.to() != null) {
                conditions.add(cb.lessThan(root.<Instant>get("createdAt"), filter.to()));
            }
            if (filter.reference() != null) {
                conditions.add(cb.like(root.<String>get("reference"),
                        LikePatterns.contains(filter.reference()), LikePatterns.ESCAPE));
            }
            return cb.and(conditions.toArray(Predicate[]::new));
        };
    }
}
