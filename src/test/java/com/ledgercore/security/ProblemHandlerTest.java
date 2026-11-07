package com.ledgercore.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ledgercore.audit.AuditAction;
import com.ledgercore.audit.AuditService;
import com.ledgercore.user.Role;
import com.ledgercore.user.User;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class SecurityProblemHandlerTest {

    @Mock private AuditService auditService;

    private SecurityProblemHandler handler;

    @BeforeEach
    void setUp() {
        ObjectMapper mapper = Jackson2ObjectMapperBuilder.json().build();
        handler = new SecurityProblemHandler(mapper, auditService);
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    private void signInAs(UUID userId) {
        User user = new User("someone@example.com", "hash", "Someone", Role.USER);
        ReflectionTestUtils.setField(user, "id", userId);
        UserPrincipal principal = UserPrincipal.from(user);
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated(principal, null, principal.getAuthorities()));
    }

    @Test
    void deniedAdminAccessIsAuditedWithTheActor() throws Exception {
        UUID actor = UUID.randomUUID();
        signInAs(actor);

        handler.handle(new MockHttpServletRequest("GET", "/api/v1/admin/users"),
                new MockHttpServletResponse(), new AccessDeniedException("denied"));

        verify(auditService).recordBestEffort(eq(actor), eq(AuditAction.ADMIN_ACCESS_DENIED),
                isNull(), isNull(), isNull(), eq("GET /api/v1/admin/users"));
    }

    @Test
    void deniedAccessOutsideAdminPathsIsNotAudited() throws Exception {
        signInAs(UUID.randomUUID());

        handler.handle(new MockHttpServletRequest("GET", "/api/v1/wallets"),
                new MockHttpServletResponse(), new AccessDeniedException("denied"));

        verify(auditService, never()).recordBestEffort(any(), any(), any(), any(), any(), any());
    }

    @Test
    void theActorIsEmptyWhenNobodyIsSignedIn() throws Exception {
        handler.handle(new MockHttpServletRequest("POST", "/api/v1/admin/users/x/disable"),
                new MockHttpServletResponse(), new AccessDeniedException("denied"));

        verify(auditService).recordBestEffort(isNull(), eq(AuditAction.ADMIN_ACCESS_DENIED),
                isNull(), isNull(), isNull(), eq("POST /api/v1/admin/users/x/disable"));
    }

    @Test
    void theResponseIsA403ProblemDocument() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();

        handler.handle(new MockHttpServletRequest("GET", "/api/v1/wallets"), response,
                new AccessDeniedException("denied"));

        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(response.getContentType()).startsWith("application/problem+json");
        assertThat(response.getContentAsString()).contains("permission");
    }
}