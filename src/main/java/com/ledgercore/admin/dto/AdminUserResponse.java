package com.ledgercore.admin.dto;

import com.ledgercore.user.Role;
import com.ledgercore.user.User;

import java.time.Instant;
import java.util.UUID;

/** What an admin sees about a user. There is no password field, so there is nothing to leak. */
public record AdminUserResponse(UUID id,
                                String email,
                                String fullName,
                                Role role,
                                boolean enabled,
                                Instant createdAt,
                                Instant updatedAt) {

    public static AdminUserResponse from(User user) {
        return new AdminUserResponse(user.getId(), user.getEmail(), user.getFullName(), user.getRole(),
                user.isEnabled(), user.getCreatedAt(), user.getUpdatedAt());
    }
}