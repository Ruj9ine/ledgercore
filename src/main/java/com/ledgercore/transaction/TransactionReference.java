package com.ledgercore.transaction;

import java.security.SecureRandom;

/** Generates human-friendly transaction references such as TXN-7K2M9QX4PD. */
final class TransactionReference {

    // No 0/O or 1/I/L, so references are hard to misread when read aloud or typed from a receipt.
    private static final String ALPHABET = "23456789ABCDEFGHJKMNPQRSTUVWXYZ";
    private static final int LENGTH = 10;
    private static final SecureRandom RANDOM = new SecureRandom();

    private TransactionReference() {
    }

    static String generate() {
        StringBuilder reference = new StringBuilder("TXN-");
        for (int i = 0; i < LENGTH; i++) {
            reference.append(ALPHABET.charAt(RANDOM.nextInt(ALPHABET.length())));
        }
        return reference.toString();
    }
}
