package com.ledgercore.common;

public class InvalidIdempotencyKeyException extends RuntimeException {

    public InvalidIdempotencyKeyException() {
        super("The Idempotency-Key header must be 1 to 100 visible characters with no spaces.");
    }
}