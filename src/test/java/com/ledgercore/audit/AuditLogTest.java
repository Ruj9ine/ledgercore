package com.ledgercore.audit;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class AuditLogTest {

    private final UUID actor = UUID.randomUUID();
    private final UUID target = UUID.randomUUID();

    @Test
    void keepsEveryFieldItWasGiven() {
        AuditLog entry = AuditLog.of(actor, AuditAction.WALLET_FROZEN, AuditTargetType.WALLET, target,
                "suspected fraud", "was ACTIVE");

        assertThat(entry.getActorUserId()).isEqualTo(actor);
        assertThat(entry.getAction()).isEqualTo(AuditAction.WALLET_FROZEN);
        assertThat(entry.getTargetType()).isEqualTo(AuditTargetType.WALLET);
        assertThat(entry.getTargetId()).isEqualTo(target);
        assertThat(entry.getReason()).isEqualTo("suspected fraud");
        assertThat(entry.getDetail()).isEqualTo("was ACTIVE");
    }

    @Test
    void cutsOverlongReasonAndDetailToTheColumnSize() {
        AuditLog entry = AuditLog.of(actor, AuditAction.REQUEST_REJECTED, null, null,
                "r".repeat(600), "d".repeat(2000));

        assertThat(entry.getReason()).hasSize(500);
        assertThat(entry.getDetail()).hasSize(1000);
    }

    @Test
    void absentValuesStayAbsent() {
        AuditLog entry = AuditLog.of(null, AuditAction.ADMIN_BOOTSTRAPPED, null, null, null, null);

        assertThat(entry.getActorUserId()).isNull();
        assertThat(entry.getReason()).isNull();
        assertThat(entry.getDetail()).isNull();
    }
}
