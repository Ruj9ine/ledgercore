package com.ledgercore.admin.dto;

import com.ledgercore.audit.AuditAction;
import com.ledgercore.audit.AuditLog;
import com.ledgercore.audit.AuditTargetType;

import java.time.Instant;
import java.util.UUID;

public record AuditEntryResponse(UUID id,
                                 Instant occurredAt,
                                 UUID actorUserId,
                                 AuditAction action,
                                 AuditTargetType targetType,
                                 UUID targetId,
                                 String reason,
                                 String detail) {

    public static AuditEntryResponse from(AuditLog entry) {
        return new AuditEntryResponse(entry.getId(), entry.getOccurredAt(), entry.getActorUserId(), entry.getAction(),
                entry.getTargetType(), entry.getTargetId(), entry.getReason(), entry.getDetail());
    }
}
