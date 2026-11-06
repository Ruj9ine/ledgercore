package com.ledgercore.common;

public class WalletAlreadyExistsException extends RuntimeException {

    public WalletAlreadyExistsException(String currency) {
        super("You already have a " + currency + " wallet.");
    }
}
