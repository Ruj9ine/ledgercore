package com.ledgercore.transaction;

import com.ledgercore.common.InvalidIdempotencyKeyException;

import java.util.regex.Pattern;

final class IdempotencyKeys {

    /** 1 to 100 visible ASCII characters, no spaces. */
    private static final Pattern VALID = Pattern.compile("[\\x21-\\x7E]{1,100}");

    private IdempotencyKeys() {
    }

    static void requireValid(String key) {
        if (key == null || !VALID.matcher(key).matches()) {
            throw new InvalidIdempotencyKeyException();
        }
    }
}