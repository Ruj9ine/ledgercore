package com.ledgercore.common;

/** Builds safe patterns for SQL LIKE searches. */
public final class LikePatterns {

    public static final char ESCAPE = '\\';

    private LikePatterns() {
    }

    /**
     * Turns the text into a "contains" pattern in which every character means itself. In LIKE, % and _ are
     * wildcards, and _ is a perfectly normal character in an email address, so they must be escaped.
     */
    public static String contains(String text) {
        String escaped = text.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
        return "%" + escaped + "%";
    }
}
