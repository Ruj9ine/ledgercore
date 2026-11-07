package com.ledgercore.reconciliation;

import java.time.Instant;
import java.util.List;

public record ReconciliationReport(Instant checkedAt, boolean balanced, List<CheckResult> checks) {
}
