package com.ledgercore.common;

/** The request is valid, but the target is not in a state where it can be applied. */
public class StateConflictException extends RuntimeException {

    public StateConflictException(String message) {
        super(message);
    }
}
