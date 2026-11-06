package com.ledgercore.security;

import com.ledgercore.user.Role;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtServiceTest {

    private static final String SECRET = "unit-test-secret-unit-test-secret-1234";

    private static JwtService service(String secret, Clock clock) {
        return new JwtService(new JwtProperties(secret, 60), clock);
    }

    @Test
    void tokenRoundTripsToTheSameUserId() {
        JwtService service = service(SECRET, Clock.systemUTC());
        UUID userId = UUID.randomUUID();

        String token = service.generateToken(userId, Role.USER);

        assertThat(service.parseUserId(token)).contains(userId);
    }

    @Test
    void expiredTokenIsRejected() {
        Clock twoHoursAgo = Clock.fixed(Instant.now().minus(Duration.ofHours(2)), ZoneOffset.UTC);
        String token = service(SECRET, twoHoursAgo).generateToken(UUID.randomUUID(), Role.USER);

        Optional<UUID> result = service(SECRET, Clock.systemUTC()).parseUserId(token);

        assertThat(result).isEmpty();
    }

    @Test
    void tokenSignedWithAnotherSecretIsRejected() {
        String token = service(SECRET, Clock.systemUTC()).generateToken(UUID.randomUUID(), Role.USER);

        Optional<UUID> result = service("a-completely-different-secret-of-sufficient-length", Clock.systemUTC())
                .parseUserId(token);

        assertThat(result).isEmpty();
    }

    @Test
    void garbageTokenIsRejected() {
        assertThat(service(SECRET, Clock.systemUTC()).parseUserId("not.a.jwt")).isEmpty();
    }

    @Test
    void secretShorterThan32BytesIsRejectedAtStartup() {
        assertThatThrownBy(() -> service("too-short", Clock.systemUTC()))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
