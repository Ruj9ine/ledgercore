package com.ledgercore.common;

public class SameWalletTransferException extends RuntimeException {

    public SameWalletTransferException() {
        super("Source and destination wallets must be different.");
    }
}