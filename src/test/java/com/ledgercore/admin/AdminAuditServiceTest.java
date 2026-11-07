package com.ledgercore.admin;

import com.ledgercore.admin.dto.AuditEntryResponse;
import com.ledgercore.audit.AuditAction;
import com.ledgercore.audit.AuditLog;
import com.ledgercore.audit.AuditLogRepository;
import com.ledgercore.audit.AuditTargetType;
import com.ledgercore.common.PageResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminAuditServiceTest {

    @Mock private AuditLogRepository auditLogRepository;

    @InjectMocks private AdminAuditService service;

    @Test
    void theLogIsNewestFirstWithAnIdTieBreakerAndUsesTheRequestedPage() {
        when(auditLogRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<AuditLog>(List.of()));

        service.list(AuditFilter.of(null, null, null, null, null, 3, 25));

        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(auditLogRepository).findAll(any(Specification.class), pageable.capture());
        assertThat(pageable.getValue().getPageNumber()).isEqualTo(3);
        assertThat(pageable.getValue().getPageSize()).isEqualTo(25);
        Sort sort = pageable.getValue().getSort();
        assertThat(sort.stream().map(Sort.Order::getProperty)).containsExactly("occurredAt", "id");
        assertThat(sort.getOrderFor("occurredAt").isDescending()).isTrue();
        assertThat(sort.getOrderFor("id").isDescending()).isTrue();
    }

    @Test
    void entriesAreMappedIntoOurOwnPageShape() {
        UUID actor = UUID.randomUUID();
        UUID target = UUID.randomUUID();
        AuditLog entry = AuditLog.of(actor, AuditAction.WALLET_FROZEN, AuditTargetType.WALLET, target,
                "fraud review", "was ACTIVE");
        when(auditLogRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<AuditLog>(List.of(entry), PageRequest.of(0, 20), 1));

        PageResponse<AuditEntryResponse> response = service.list(AuditFilter.of(null, null, null, null, null, null, null));

        assertThat(response.totalElements()).isEqualTo(1);
        AuditEntryResponse item = response.items().get(0);
        assertThat(item.actorUserId()).isEqualTo(actor);
        assertThat(item.action()).isEqualTo(AuditAction.WALLET_FROZEN);
        assertThat(item.targetType()).isEqualTo(AuditTargetType.WALLET);
        assertThat(item.targetId()).isEqualTo(target);
        assertThat(item.reason()).isEqualTo("fraud review");
        assertThat(item.detail()).isEqualTo("was ACTIVE");
    }
}