package com.ledgercore.common;

public class CurrencyMismatchException extends RuntimeException {

    public CurrencyMismatchException() {
        super("Transfers are only supported between wallets of the same currency.");
    }
}