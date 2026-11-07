package com.ledgercore.admin;

import com.ledgercore.common.InvalidQueryException;
import com.ledgercore.user.Role;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class UserFilterTest {

    @Test
    void defaultsAreAppliedWhenNothingIsGiven() {
        UserFilter filter = UserFilter.of(null, null, null, null, null);

        assertThat(filter.email()).isNull();
        assertThat(filter.role()).isNull();
        assertThat(filter.enabled()).isNull();
        assertThat(filter.page()).isZero();
        assertThat(filter.size()).isEqualTo(20);
    }

    @Test
    void validValuesAreKept() {
        UserFilter filter = UserFilter.of("alice", Role.USER, true, 2, 50);

        assertThat(filter.email()).isEqualTo("alice");
        assertThat(filter.role()).isEqualTo(Role.USER);
        assertThat(filter.enabled()).isTrue();
        assertThat(filter.page()).isEqualTo(2);
        assertThat(filter.size()).isEqualTo(50);
    }

    @Test
    void emailIsTrimmedAndLowercasedAndBlankMeansNoFilter() {
        assertThat(UserFilter.of("  Alice@Example.COM ", null, null, null, null).email()).isEqualTo("alice@example.com");
        assertThat(UserFilter.of("   ", null, null, null, null).email()).isNull();
    }

    @Test
    void aTooLongEmailSearchIsRejected() {
        assertThatThrownBy(() -> UserFilter.of("a".repeat(101), null, null, null, null))
                .isInstanceOf(InvalidQueryException.class);
    }

    @Test
    void invalidPagingIsRejected() {
        assertThatThrownBy(() -> UserFilter.of(null, null, null, 0, 0))
                .isInstanceOf(InvalidQueryException.class);
    }
}
