package com.ledgercore.audit;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import org.hibernate.annotations.Immutable;

import java.time.Instant;
import java.util.UUID;

/** One immutable line in the audit trail. Rows are only ever inserted, never updated or deleted. */
@Entity
@Immutable
@Table(name = "audit_log")
public class AuditLog {

    static final int MAX_REASON_LENGTH = 500;
    static final int MAX_DETAIL_LENGTH = 1000;

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    @Column(name = "actor_user_id")
    private UUID actorUserId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private AuditAction action;

    @Enumerated(EnumType.STRING)
    @Column(name = "target_type", length = 20)
    private AuditTargetType targetType;

    @Column(name = "target_id")
    private UUID targetId;

    @Column(length = MAX_REASON_LENGTH)
    private String reason;

    @Column(length = MAX_DETAIL_LENGTH)
    private String detail;

    protected AuditLog() {
        // required by JPA
    }

    private AuditLog(UUID actorUserId, AuditAction action, AuditTargetType targetType, UUID targetId,
                     String reason, String detail) {
        this.actorUserId = actorUserId;
        this.action = action;
        this.targetType = targetType;
        this.targetId = targetId;
        this.reason = reason;
        this.detail = detail;
    }

    public static AuditLog of(UUID actorUserId, AuditAction action, AuditTargetType targetType, UUID targetId,
                              String reason, String detail) {
        // Some of this text can come from a request, so it is cut to the column size instead of failing the insert.
        return new AuditLog(actorUserId, action, targetType, targetId,
                limit(reason, MAX_REASON_LENGTH), limit(detail, MAX_DETAIL_LENGTH));
    }

    private static String limit(String value, int max) {
        return value == null || value.length() <= max ? value : value.substring(0, max);
    }

    @PrePersist
    void onCreate() {
        this.occurredAt = Instant.now();
    }

    public UUID getId() { return id; }
    public Instant getOccurredAt() { return occurredAt; }
    public UUID getActorUserId() { return actorUserId; }
    public AuditAction getAction() { return action; }
    public AuditTargetType getTargetType() { return targetType; }
    public UUID getTargetId() { return targetId; }
    public String getReason() { return reason; }
    public String getDetail() { return detail; }
}
