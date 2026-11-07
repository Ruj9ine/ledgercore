package com.ledgercore.admin;

import com.ledgercore.audit.AuditAction;
import com.ledgercore.audit.AuditService;
import com.ledgercore.audit.AuditTargetType;
import com.ledgercore.user.Role;
import com.ledgercore.user.User;
import com.ledgercore.user.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminBootstrapTest {

    private static final String PASSWORD = "a-long-admin-password";

    @Mock private UserRepository userRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private AuditService auditService;

    private AdminBootstrap bootstrap(String email, String password) {
        return new AdminBootstrap(new AdminProperties(email, password), userRepository, passwordEncoder, auditService);
    }

    @Test
    void doesNothingWhenNoAdminIsConfigured() {
        bootstrap("", "").run(null);
        bootstrap(null, null).run(null);

        verify(userRepository, never()).saveAndFlush(any(User.class));
    }

    @Test
    void createsAnAdminWithAnEncodedPasswordAndRecordsIt() {
        when(userRepository.findByEmail("root@example.com")).thenReturn(Optional.empty());
        when(passwordEncoder.encode(PASSWORD)).thenReturn("encoded-value");
        when(userRepository.saveAndFlush(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        bootstrap("root@example.com", PASSWORD).run(null);

        ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
        verify(userRepository).saveAndFlush(saved.capture());
        assertThat(saved.getValue().getRole()).isEqualTo(Role.ADMIN);
        assertThat(saved.getValue().getPasswordHash()).isEqualTo("encoded-value");
        assertThat(saved.getValue().isEnabled()).isTrue();
        verify(auditService).record(isNull(), eq(AuditAction.ADMIN_BOOTSTRAPPED), eq(AuditTargetType.USER),
                any(), isNull(), isNull());
    }

    @Test
    void normalizesTheEmail() {
        when(userRepository.findByEmail("root@example.com")).thenReturn(Optional.empty());
        when(passwordEncoder.encode(PASSWORD)).thenReturn("encoded-value");
        when(userRepository.saveAndFlush(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        bootstrap("  Root@Example.COM ", PASSWORD).run(null);

        ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
        verify(userRepository).saveAndFlush(saved.capture());
        assertThat(saved.getValue().getEmail()).isEqualTo("root@example.com");
    }

    @Test
    void neverPromotesAnExistingUser() {
        User ordinary = new User("root@example.com", "hash", "Someone", Role.USER);
        when(userRepository.findByEmail("root@example.com")).thenReturn(Optional.of(ordinary));

        bootstrap("root@example.com", PASSWORD).run(null);

        assertThat(ordinary.getRole()).isEqualTo(Role.USER);
        verify(userRepository, never()).saveAndFlush(any(User.class));
        verify(auditService, never()).record(any(), any(), any(), any(), any(), any());
    }

    @Test
    void leavesAnExistingAdminAlone() {
        User admin = new User("root@example.com", "hash", "Administrator", Role.ADMIN);
        when(userRepository.findByEmail("root@example.com")).thenReturn(Optional.of(admin));

        bootstrap("root@example.com", PASSWORD).run(null);

        verify(userRepository, never()).saveAndFlush(any(User.class));
        verify(passwordEncoder, never()).encode(any());
    }

    @ParameterizedTest
    @CsvSource(value = {
            "root@example.com,",
            ",a-long-admin-password"
    })
    void refusesToStartWhenOnlyOneOfEmailAndPasswordIsSet(String email, String password) {
        assertThatThrownBy(() -> bootstrap(email, password).run(null))
                .isInstanceOf(IllegalStateException.class);

        verify(userRepository, never()).saveAndFlush(any(User.class));
    }

    @Test
    void refusesAShortPassword() {
        assertThatThrownBy(() -> bootstrap("root@example.com", "short-pass1").run(null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("at least 12");

        verify(userRepository, never()).saveAndFlush(any(User.class));
    }

    @Test
    void refusesAPasswordThatBcryptWouldSilentlyTruncate() {
        assertThatThrownBy(() -> bootstrap("root@example.com", "x".repeat(73)).run(null))
                .isInstanceOf(IllegalStateException.class);

        verify(userRepository, never()).saveAndFlush(any(User.class));
    }
}
