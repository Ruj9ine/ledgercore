package com.ledgercore.transaction;

import com.ledgercore.common.InvalidIdempotencyKeyException;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class IdempotencyKeysTest {

    @Test
    void typicalKeysAreAccepted() {
        assertThatCode(() -> IdempotencyKeys.requireValid("deposit-2026-11-06-001")).doesNotThrowAnyException();
        assertThatCode(() -> IdempotencyKeys.requireValid(UUID.randomUUID().toString())).doesNotThrowAnyException();
        assertThatCode(() -> IdempotencyKeys.requireValid("x".repeat(100))).doesNotThrowAnyException();
    }

    @Test
    void invalidKeysAreRejected() {
        for (String key : Arrays.asList(null, "", " ", "has space", "tab\there", "x".repeat(101), "naïve")) {
            assertThatThrownBy(() -> IdempotencyKeys.requireValid(key))
                    .as("key: %s", key)
                    .isInstanceOf(InvalidIdempotencyKeyException.class);
        }
    }
}