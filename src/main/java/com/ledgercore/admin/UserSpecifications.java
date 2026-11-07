package com.ledgercore.admin;

import com.ledgercore.common.LikePatterns;
import com.ledgercore.user.User;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;

final class UserSpecifications {

    private UserSpecifications() {
    }

    static Specification<User> matching(UserFilter filter) {
        return (root, query, cb) -> {
            List<Predicate> conditions = new ArrayList<>();
            if (filter.email() != null) {
                conditions.add(cb.like(cb.lower(root.<String>get("email")),
                        LikePatterns.contains(filter.email()), LikePatterns.ESCAPE));
            }
            if (filter.role() != null) {
                conditions.add(cb.equal(root.get("role"), filter.role()));
            }
            if (filter.enabled() != null) {
                conditions.add(cb.equal(root.get("enabled"), filter.enabled()));
            }
            return cb.and(conditions.toArray(Predicate[]::new));
        };
    }
}
