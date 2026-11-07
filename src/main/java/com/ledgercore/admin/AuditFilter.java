package com.ledgercore.admin;

import com.ledgercore.audit.AuditAction;
import com.ledgercore.common.InvalidQueryException;
import com.ledgercore.common.PageParams;

import java.time.Instant;
import java.util.UUID;

/** The validated search and paging options for the audit log. */
public record AuditFilter(AuditAction action, UUID actorUserId, UUID targetId, Instant from, Instant to,
                          int page, int size) {

    public static AuditFilter of(AuditAction action, UUID actorUserId, UUID targetId, Instant from, Instant to,
                                 Integer page, Integer size) {
        PageParams paging = PageParams.of(page, size);
        if (from != null && to != null && !from.isBefore(to)) {
            throw new InvalidQueryException("from must be earlier than to.");
        }
        return new AuditFilter(action, actorUserId, targetId, from, to, paging.page(), paging.size());
    }
}
