package com.ledgercore.admin;

import com.ledgercore.admin.dto.AuditEntryResponse;
import com.ledgercore.audit.AuditLog;
import com.ledgercore.audit.AuditLogRepository;
import com.ledgercore.common.PageResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AdminAuditService {

    private final AuditLogRepository auditLogRepository;

    public AdminAuditService(AuditLogRepository auditLogRepository) {
        this.auditLogRepository = auditLogRepository;
    }

    @Transactional(readOnly = true)
    public PageResponse<AuditEntryResponse> list(AuditFilter filter) {
        Pageable pageable = PageRequest.of(filter.page(), filter.size(),
                Sort.by(Sort.Order.desc("occurredAt"), Sort.Order.desc("id")));
        Page<AuditLog> entries = auditLogRepository.findAll(AuditSpecifications.matching(filter), pageable);
        return PageResponse.from(entries.map(AuditEntryResponse::from));
    }
}
