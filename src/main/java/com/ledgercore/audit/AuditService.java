package com.ledgercore.audit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class AuditService {

    private static final Logger log = LoggerFactory.getLogger(AuditService.class);

    private final AuditLogRepository auditLogRepository;

    public AuditService(AuditLogRepository auditLogRepository) {
        this.auditLogRepository = auditLogRepository;
    }

    /**
     * Writes an audit entry. Inside a business transaction it joins that transaction, so an admin action and
     * its audit entry are committed or rolled back together.
     */
    @Transactional
    public void record(UUID actorUserId, AuditAction action, AuditTargetType targetType, UUID targetId,
                       String reason, String detail) {
        auditLogRepository.save(AuditLog.of(actorUserId, action, targetType, targetId, reason, detail));
        log.info("AUDIT {} actor={} target={}:{}", action, actorUserId, targetType, targetId);
    }

    /**
     * For events recorded after a business transaction has already ended, such as a rejected request.
     * An audit failure is logged but never changes what the user gets back. Never call this from inside a
     * transaction: a failed insert would mark that transaction as rollback-only.
     */
    public void recordBestEffort(UUID actorUserId, AuditAction action, AuditTargetType targetType, UUID targetId,
                                 String reason, String detail) {
        try {
            record(actorUserId, action, targetType, targetId, reason, detail);
        } catch (RuntimeException e) {
            log.error("Could not write audit entry {} for actor={}", action, actorUserId, e);
        }
    }
}
