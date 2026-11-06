package com.ledgercore.common;

public class IdempotencyConflictException extends RuntimeException {

    public IdempotencyConflictException() {
        super("This Idempotency-Key was already used for a different request.");
    }
}
