package com.ledgercore.admin;

import com.ledgercore.common.InvalidQueryException;
import com.ledgercore.wallet.SupportedCurrency;
import com.ledgercore.wallet.WalletStatus;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class WalletFilterTest {

    @Test
    void defaultsAreAppliedWhenNothingIsGiven() {
        WalletFilter filter = WalletFilter.of(null, null, null, null, null);

        assertThat(filter.userId()).isNull();
        assertThat(filter.currency()).isNull();
        assertThat(filter.status()).isNull();
        assertThat(filter.page()).isZero();
        assertThat(filter.size()).isEqualTo(20);
    }

    @Test
    void validValuesAreKept() {
        UUID userId = UUID.randomUUID();

        WalletFilter filter = WalletFilter.of(userId, SupportedCurrency.UGX, WalletStatus.FROZEN, 2, 50);

        assertThat(filter.userId()).isEqualTo(userId);
        assertThat(filter.currency()).isEqualTo(SupportedCurrency.UGX);
        assertThat(filter.status()).isEqualTo(WalletStatus.FROZEN);
        assertThat(filter.page()).isEqualTo(2);
        assertThat(filter.size()).isEqualTo(50);
    }

    @Test
    void invalidPagingIsRejected() {
        assertThatThrownBy(() -> WalletFilter.of(null, null, null, -1, 20))
                .isInstanceOf(InvalidQueryException.class);
    }
}
