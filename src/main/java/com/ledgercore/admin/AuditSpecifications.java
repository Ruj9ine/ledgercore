package com.ledgercore.admin;

import com.ledgercore.audit.AuditLog;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

final class AuditSpecifications {

    private AuditSpecifications() {
    }

    static Specification<AuditLog> matching(AuditFilter filter) {
        return (root, query, cb) -> {
            List<Predicate> conditions = new ArrayList<>();
            if (filter.action() != null) {
                conditions.add(cb.equal(root.get("action"), filter.action()));
            }
            if (filter.actorUserId() != null) {
                conditions.add(cb.equal(root.get("actorUserId"), filter.actorUserId()));
            }
            if (filter.targetId() != null) {
                conditions.add(cb.equal(root.get("targetId"), filter.targetId()));
            }
            if (filter.from() != null) {
                conditions.add(cb.greaterThanOrEqualTo(root.<Instant>get("occurredAt"), filter.from()));
            }
            if (filter.to() != null) {
                conditions.add(cb.lessThan(root.<Instant>get("occurredAt"), filter.to()));
            }
            return cb.and(conditions.toArray(Predicate[]::new));
        };
    }
}
