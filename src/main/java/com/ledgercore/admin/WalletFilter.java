package com.ledgercore.admin;

import com.ledgercore.common.PageParams;
import com.ledgercore.wallet.SupportedCurrency;
import com.ledgercore.wallet.WalletStatus;

import java.util.UUID;

/** The validated search and paging options for the admin wallet list. */
public record WalletFilter(UUID userId, SupportedCurrency currency, WalletStatus status, int page, int size) {

    public static WalletFilter of(UUID userId, SupportedCurrency currency, WalletStatus status,
                                  Integer page, Integer size) {
        PageParams paging = PageParams.of(page, size);
        return new WalletFilter(userId, currency, status, paging.page(), paging.size());
    }
}
