package com.ledgercore.admin;

import com.ledgercore.wallet.Wallet;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;

final class WalletSpecifications {

    private WalletSpecifications() {
    }

    static Specification<Wallet> matching(WalletFilter filter) {
        return (root, query, cb) -> {
            List<Predicate> conditions = new ArrayList<>();
            if (filter.userId() != null) {
                conditions.add(cb.equal(root.get("userId"), filter.userId()));
            }
            if (filter.currency() != null) {
                conditions.add(cb.equal(root.get("currency"), filter.currency()));
            }
            if (filter.status() != null) {
                conditions.add(cb.equal(root.get("status"), filter.status()));
            }
            return cb.and(conditions.toArray(Predicate[]::new));
        };
    }
}
