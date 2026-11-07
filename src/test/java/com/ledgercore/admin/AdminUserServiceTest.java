package com.ledgercore.admin;

import com.ledgercore.admin.dto.AdminUserResponse;
import com.ledgercore.audit.AuditAction;
import com.ledgercore.audit.AuditService;
import com.ledgercore.audit.AuditTargetType;
import com.ledgercore.common.ResourceNotFoundException;
import com.ledgercore.common.StateConflictException;
import com.ledgercore.user.Role;
import com.ledgercore.user.User;
import com.ledgercore.user.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminUserServiceTest {

    @Mock private UserRepository userRepository;
    @Mock private AuditService auditService;

    @InjectMocks private AdminUserService service;

    private final UUID actor = UUID.randomUUID();
    private final UUID targetId = UUID.randomUUID();

    private User target() {
        User user = new User("target@example.com", "hash", "Target", Role.USER);
        ReflectionTestUtils.setField(user, "id", targetId);
        return user;
    }

    private void verifyNothingWasAudited() {
        verify(auditService, never()).record(any(), any(), any(), any(), any(), any());
    }

    @Test
    void disableSwitchesTheUserOffAndRecordsATrimmedReason() {
        User user = target();
        when(userRepository.findByIdForUpdate(targetId)).thenReturn(Optional.of(user));

        AdminUserResponse response = service.disable(actor, targetId, "  fraud suspected  ");

        assertThat(user.isEnabled()).isFalse();
        assertThat(response.enabled()).isFalse();
        verify(auditService).record(eq(actor), eq(AuditAction.USER_DISABLED), eq(AuditTargetType.USER),
                eq(targetId), eq("fraud suspected"), eq("was enabled"));
    }

    @Test
    void anAdminCannotDisableThemselvesAndNothingIsLoaded() {
        assertThatThrownBy(() -> service.disable(actor, actor, "oops"))
                .isInstanceOf(StateConflictException.class);

        verify(userRepository, never()).findByIdForUpdate(any());
        verifyNothingWasAudited();
    }

    @Test
    void disablingAnAlreadyDisabledUserIsAConflictAndWritesNoAudit() {
        User user = target();
        user.setEnabled(false);
        when(userRepository.findByIdForUpdate(targetId)).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> service.disable(actor, targetId, "again"))
                .isInstanceOf(StateConflictException.class);

        verifyNothingWasAudited();
    }

    @Test
    void disablingAnUnknownUserIsNotFound() {
        when(userRepository.findByIdForUpdate(targetId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.disable(actor, targetId, "who"))
                .isInstanceOf(ResourceNotFoundException.class);

        verifyNothingWasAudited();
    }

    @Test
    void enableSwitchesTheUserBackOnAndRecordsIt() {
        User user = target();
        user.setEnabled(false);
        when(userRepository.findByIdForUpdate(targetId)).thenReturn(Optional.of(user));

        AdminUserResponse response = service.enable(actor, targetId, "restored after review");

        assertThat(user.isEnabled()).isTrue();
        assertThat(response.enabled()).isTrue();
        verify(auditService).record(eq(actor), eq(AuditAction.USER_ENABLED), eq(AuditTargetType.USER),
                eq(targetId), eq("restored after review"), eq("was disabled"));
    }

    @Test
    void enablingAnEnabledUserIsAConflictAndWritesNoAudit() {
        when(userRepository.findByIdForUpdate(targetId)).thenReturn(Optional.of(target()));

        assertThatThrownBy(() -> service.enable(actor, targetId, "already fine"))
                .isInstanceOf(StateConflictException.class);

        verifyNothingWasAudited();
    }

    @Test
    void gettingAnUnknownUserIsNotFound() {
        when(userRepository.findById(targetId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.get(targetId)).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void theListIsNewestFirstWithAnIdTieBreakerAndUsesTheRequestedPage() {
        when(userRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<User>(List.of()));

        service.list(UserFilter.of(null, null, null, 1, 5));

        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(userRepository).findAll(any(Specification.class), pageable.capture());
        assertThat(pageable.getValue().getPageNumber()).isEqualTo(1);
        assertThat(pageable.getValue().getPageSize()).isEqualTo(5);
        Sort sort = pageable.getValue().getSort();
        assertThat(sort.stream().map(Sort.Order::getProperty)).containsExactly("createdAt", "id");
        assertThat(sort.getOrderFor("createdAt").isDescending()).isTrue();
        assertThat(sort.getOrderFor("id").isDescending()).isTrue();
    }
}
