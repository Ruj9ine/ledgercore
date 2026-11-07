package com.ledgercore.transaction;

import com.ledgercore.common.InvalidQueryException;
import com.ledgercore.common.PageParams;

import java.time.Instant;
import java.util.Locale;
import java.util.regex.Pattern;

/** The validated search and paging options for a wallet statement. Build it with {@link #of}. */
public record StatementFilter(TransactionType type,
                              LedgerDirection direction,
                              Instant from,
                              Instant to,
                              String reference,
                              int page,
                              int size) {

    public static final int DEFAULT_SIZE = PageParams.DEFAULT_SIZE;
    public static final int MAX_SIZE = PageParams.MAX_SIZE;

    // References contain only these characters, so a search needs no wildcard escaping.
    private static final Pattern REFERENCE_SEARCH = Pattern.compile("[A-Z0-9-]{1,20}");

    public static StatementFilter of(TransactionType type, LedgerDirection direction, Instant from, Instant to,
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
        return new StatementFilter(type, direction, from, to, normalizedReference, paging.page(), paging.size());
    }
}