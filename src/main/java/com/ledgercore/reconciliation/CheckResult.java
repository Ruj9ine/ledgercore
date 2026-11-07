package com.ledgercore.reconciliation;

import java.util.List;

/** The outcome of one reconciliation check. {@code samples} shows at most the first few problems found. */
public record CheckResult(String name, String description, boolean passed, long problemCount, List<String> samples) {
}