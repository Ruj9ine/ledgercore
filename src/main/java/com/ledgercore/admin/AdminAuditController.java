package com.ledgercore.admin;

import com.ledgercore.admin.dto.AuditEntryResponse;
import com.ledgercore.audit.AuditAction;
import com.ledgercore.common.PageResponse;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin/audit-log")
@PreAuthorize("hasRole('ADMIN')")
public class AdminAuditController {

    private final AdminAuditService adminAuditService;

    public AdminAuditController(AdminAuditService adminAuditService) {
        this.adminAuditService = adminAuditService;
    }

    @GetMapping
    public PageResponse<AuditEntryResponse> list(@RequestParam(required = false) AuditAction action,
                                                 @RequestParam(required = false) UUID actorUserId,
                                                 @RequestParam(required = false) UUID targetId,
                                                 @RequestParam(required = false) Instant from,
                                                 @RequestParam(required = false) Instant to,
                                                 @RequestParam(required = false) Integer page,
                                                 @RequestParam(required = false) Integer size) {
        return adminAuditService.list(AuditFilter.of(action, actorUserId, targetId, from, to, page, size));
    }
}