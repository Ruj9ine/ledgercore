package com.ledgercore.admin;

import com.ledgercore.audit.AuditAction;
import com.ledgercore.common.InvalidQueryException;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AuditFilterTest {

    private static final Instant EARLIER = Instant.parse("2026-11-01T00:00:00Z");
    private static final Instant LATER = Instant.parse("2026-11-02T00:00:00Z");

    @Test
    void defaultsAreAppliedWhenNothingIsGiven() {
        AuditFilter filter = AuditFilter.of(null, null, null, null, null, null, null);

        assertThat(filter.action()).isNull();
        assertThat(filter.actorUserId()).isNull();
        assertThat(filter.targetId()).isNull();
        assertThat(filter.page()).isZero();
        assertThat(filter.size()).isEqualTo(20);
    }

    @Test
    void validValuesAreKept() {
        UUID actor = UUID.randomUUID();
        UUID target = UUID.randomUUID();

        AuditFilter filter = AuditFilter.of(AuditAction.WALLET_FROZEN, actor, target, EARLIER, LATER, 1, 10);

        assertThat(filter.action()).isEqualTo(AuditAction.WALLET_FROZEN);
        assertThat(filter.actorUserId()).isEqualTo(actor);
        assertThat(filter.targetId()).isEqualTo(target);
        assertThat(filter.from()).isEqualTo(EARLIER);
        assertThat(filter.to()).isEqualTo(LATER);
        assertThat(filter.page()).isEqualTo(1);
        assertThat(filter.size()).isEqualTo(10);
    }

    @Test
    void fromMustBeEarlierThanTo() {
        assertThatThrownBy(() -> AuditFilter.of(null, null, null, LATER, EARLIER, null, null))
                .isInstanceOf(InvalidQueryException.class);
        assertThatThrownBy(() -> AuditFilter.of(null, null, null, EARLIER, EARLIER, null, null))
                .isInstanceOf(InvalidQueryException.class);
    }

    @Test
    void invalidPagingIsRejected() {
        assertThatThrownBy(() -> AuditFilter.of(null, null, null, null, null, 0, 0))
                .isInstanceOf(InvalidQueryException.class);
    }
}
