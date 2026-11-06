package com.ledgercore.transaction;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.UUID;

/** A stable SHA-256 fingerprint of what a money request asked for, used to detect misuse of an idempotency key. */
final class RequestFingerprint {

    private RequestFingerprint() {
    }

    static String of(TransactionType type, UUID walletId, BigDecimal amount) {
        // stripTrailingZeros makes "100" and "100.00" the same request.
        String canonical = type.name() + "|" + walletId + "|" + amount.stripTrailingZeros().toPlainString();
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(canonical.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }
}
