package com.ledgercore.auth;

import com.ledgercore.auth.dto.RegisterRequest;
import com.ledgercore.common.EmailAlreadyRegisteredException;
import com.ledgercore.security.JwtService;
import com.ledgercore.user.Role;
import com.ledgercore.user.User;
import com.ledgercore.user.UserRepository;
import com.ledgercore.user.dto.UserResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock private UserRepository userRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private AuthenticationManager authenticationManager;
    @Mock private JwtService jwtService;

    @InjectMocks private AuthService authService;

    @Test
    void registerNormalizesEmailHashesPasswordAndAssignsUserRole() {
        when(userRepository.existsByEmail("alice@example.com")).thenReturn(false);
        when(passwordEncoder.encode("correct-horse")).thenReturn("hashed-value");
        when(userRepository.saveAndFlush(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        UserResponse response = authService.register(
                new RegisterRequest("  Alice@Example.COM ", "correct-horse", " Alice "));

        ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
        verify(userRepository).saveAndFlush(saved.capture());
        assertThat(saved.getValue().getEmail()).isEqualTo("alice@example.com");
        assertThat(saved.getValue().getPasswordHash()).isEqualTo("hashed-value");
        assertThat(saved.getValue().getFullName()).isEqualTo("Alice");
        assertThat(saved.getValue().getRole()).isEqualTo(Role.USER);
        assertThat(response.email()).isEqualTo("alice@example.com");
    }

    @Test
    void registerRejectsDuplicateEmailWithoutSaving() {
        when(userRepository.existsByEmail("alice@example.com")).thenReturn(true);

        assertThatThrownBy(() -> authService.register(
                new RegisterRequest("Alice@example.com", "correct-horse", "Alice")))
                .isInstanceOf(EmailAlreadyRegisteredException.class);

        verify(userRepository, never()).saveAndFlush(any(User.class));
    }

    @Test
    void registerRequestToStringNeverContainsThePassword() {
        RegisterRequest request = new RegisterRequest("alice@example.com", "correct-horse", "Alice");

        assertThat(request.toString()).doesNotContain("correct-horse");
    }
}
