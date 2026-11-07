package com.ledgercore.admin;

import com.ledgercore.common.InvalidQueryException;
import com.ledgercore.common.PageParams;
import com.ledgercore.user.Role;

import java.util.Locale;

/** The validated search and paging options for the admin user list. */
public record UserFilter(String email, Role role, Boolean enabled, int page, int size) {

    static final int MAX_EMAIL_SEARCH_LENGTH = 100;

    public static UserFilter of(String email, Role role, Boolean enabled, Integer page, Integer size) {
        PageParams paging = PageParams.of(page, size);
        String normalizedEmail = null;
        if (email != null && !email.isBlank()) {
            normalizedEmail = email.trim().toLowerCase(Locale.ROOT);
            if (normalizedEmail.length() > MAX_EMAIL_SEARCH_LENGTH) {
                throw new InvalidQueryException("email search may be at most " + MAX_EMAIL_SEARCH_LENGTH + " characters.");
            }
        }
        return new UserFilter(normalizedEmail, role, enabled, paging.page(), paging.size());
    }
}
