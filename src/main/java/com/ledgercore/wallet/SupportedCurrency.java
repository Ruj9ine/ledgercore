package com.ledgercore.wallet;

import java.math.BigDecimal;

/**
 * The currencies LedgerCore supports, with the number of decimal places each allows in an amount.
 * Named to avoid confusion with java.util.Currency.
 */
public enum SupportedCurrency {

    UGX(0),
    USD(2),
    EUR(2),
    KES(2);

    private final int fractionDigits;

    SupportedCurrency(int fractionDigits) {
        this.fractionDigits = fractionDigits;
    }

    public int getFractionDigits() {
        return fractionDigits;
    }

    /** True if the amount has no more decimal places than this currency allows. Trailing zeros are ignored. */
    public boolean acceptsScaleOf(BigDecimal amount) {
        return amount.stripTrailingZeros().scale() <= fractionDigits;
    }
}
