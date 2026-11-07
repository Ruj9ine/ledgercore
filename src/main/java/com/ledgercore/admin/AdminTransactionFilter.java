package com.ledgercore.admin;

import com.ledgercore.common.InvalidQueryException;
import com.ledgercore.common.PageParams;
import com.ledgercore.transaction.TransactionType;

import java.time.Instant;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Pattern;

/** The validated search and paging options for the admin transaction list. */
public record AdminTransactionFilter(TransactionType type,
                                     UUID walletId,
                                     UUID userId,
                                     Instant from,
                                     Instant to,
                                     String reference,
                                     int page,
                                     int size) {

    private static final Pattern REFERENCE_SEARCH = Pattern.compile("[A-Z0-9-]{1,20}");

    public static AdminTransactionFilter of(TransactionType type, UUID walletId, UUID userId, Instant from, Instant to,
                                            String reference, Integer page, Integer size) {
        PageParams paging = PageParams.of(page, size);
        if (from != null && to != null && !from.isBefore(to)) {
            throw new InvalidQueryException("from must be earlier than to.");
        }
        String normalizedReference = null;
        if (reference != null && !reference.isBlank()) {
            normalizedReference = reference.trim().toUpperCase(Locale.ROOT);
            if (!REFERENCE_SEARCH.matcher(normalizedReference).matches()) {
                throw new InvalidQueryException(
                        "reference may contain only letters, digits and hyphens, up to 20 characters.");
            }
        }
        return new AdminTransactionFilter(type, walletId, userId, from, to, normalizedReference,
                paging.page(), paging.size());
    }
}
