package com.ledgercore.admin;

import com.ledgercore.admin.dto.AdminUserResponse;
import com.ledgercore.audit.AuditAction;
import com.ledgercore.audit.AuditService;
import com.ledgercore.audit.AuditTargetType;
import com.ledgercore.common.PageResponse;
import com.ledgercore.common.ResourceNotFoundException;
import com.ledgercore.common.StateConflictException;
import com.ledgercore.user.User;
import com.ledgercore.user.UserRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class AdminUserService {

    private final UserRepository userRepository;
    private final AuditService auditService;

    public AdminUserService(UserRepository userRepository, AuditService auditService) {
        this.userRepository = userRepository;
        this.auditService = auditService;
    }

    @Transactional(readOnly = true)
    public PageResponse<AdminUserResponse> list(UserFilter filter) {
        // Newest first, with the id as a tie-breaker so pages never overlap or skip rows.
        Pageable pageable = PageRequest.of(filter.page(), filter.size(),
                Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id")));
        Page<User> users = userRepository.findAll(UserSpecifications.matching(filter), pageable);
        return PageResponse.from(users.map(AdminUserResponse::from));
    }

    @Transactional(readOnly = true)
    public AdminUserResponse get(UUID userId) {
        return userRepository.findById(userId)
                .map(AdminUserResponse::from)
                .orElseThrow(() -> new ResourceNotFoundException("User not found."));
    }

    @Transactional
    public AdminUserResponse disable(UUID actorId, UUID userId, String reason) {
        if (actorId.equals(userId)) {
            // Prevents an admin locking themselves out by accident.
            throw new StateConflictException("You cannot disable your own account.");
        }
        User user = lock(userId);
        if (!user.isEnabled()) {
            throw new StateConflictException("This user is already disabled.");
        }
        user.setEnabled(false);
        userRepository.saveAndFlush(user);
        // Same database transaction as the change above: either both are saved, or neither is.
        auditService.record(actorId, AuditAction.USER_DISABLED, AuditTargetType.USER, userId, reason.trim(), "was enabled");
        return AdminUserResponse.from(user);
    }

    @Transactional
    public AdminUserResponse enable(UUID actorId, UUID userId, String reason) {
        User user = lock(userId);
        if (user.isEnabled()) {
            throw new StateConflictException("This user is already enabled.");
        }
        user.setEnabled(true);
        userRepository.saveAndFlush(user);
        auditService.record(actorId, AuditAction.USER_ENABLED, AuditTargetType.USER, userId, reason.trim(), "was disabled");
        return AdminUserResponse.from(user);
    }

    private User lock(UUID userId) {
        return userRepository.findByIdForUpdate(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found."));
    }
}
