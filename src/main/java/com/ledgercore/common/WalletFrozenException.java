package com.ledgercore.common;

public class WalletFrozenException extends RuntimeException {

    public WalletFrozenException() {
        super("This wallet is frozen.");
    }
}
