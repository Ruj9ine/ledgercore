package com.ledgercore.transaction;

import com.ledgercore.audit.AuditAction;
import com.ledgercore.audit.AuditService;
import com.ledgercore.audit.AuditTargetType;
import com.ledgercore.security.UserPrincipal;
import com.ledgercore.user.Role;
import com.ledgercore.user.User;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.servlet.HandlerMapping;

import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class RejectedMoneyRequestInterceptorTest {

    @Mock private AuditService auditService;

    private RejectedMoneyRequestInterceptor interceptor;

    private final UUID actor = UUID.randomUUID();
    private final UUID walletId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        interceptor = new RejectedMoneyRequestInterceptor(auditService);
        User user = new User("someone@example.com", "hash", "Someone", Role.USER);
        ReflectionTestUtils.setField(user, "id", actor);
        UserPrincipal principal = UserPrincipal.from(user);
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated(principal, null, principal.getAuthorities()));
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    private MockHttpServletRequest request(String method, String operation, String walletIdText) {
        MockHttpServletRequest request = new MockHttpServletRequest(method, "/api/v1/wallets/" + walletIdText + "/" + operation);
        request.setAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE, Map.of("walletId", walletIdText));
        return request;
    }

    private void finish(MockHttpServletRequest request, int status) {
        MockHttpServletResponse response = new MockHttpServletResponse();
        response.setStatus(status);
        interceptor.afterCompletion(request, response, new Object(), null);
    }

    private void verifyNothingWasAudited() {
        verify(auditService, never()).recordBestEffort(any(), any(), any(), any(), any(), any());
    }

    @Test
    void aRejectedRequestIsRecordedWithTheActorTheWalletAndTheOperation() {
        finish(request("POST", "withdrawals", walletId.toString()), 422);

        verify(auditService).recordBestEffort(eq(actor), eq(AuditAction.REQUEST_REJECTED), eq(AuditTargetType.WALLET),
                eq(walletId), isNull(), eq("withdrawals rejected with status 422"));
    }

    @ParameterizedTest
    @ValueSource(ints = {404, 409, 422})
    void businessRefusalsAreRecorded(int status) {
        finish(request("POST", "transfers", walletId.toString()), status);

        verify(auditService).recordBestEffort(eq(actor), eq(AuditAction.REQUEST_REJECTED), eq(AuditTargetType.WALLET),
                eq(walletId), isNull(), eq("transfers rejected with status " + status));
    }

    @ParameterizedTest
    @ValueSource(ints = {201, 400, 500})
    void successesMalformedInputAndServerErrorsAreNotRecorded(int status) {
        finish(request("POST", "deposits", walletId.toString()), status);

        verifyNothingWasAudited();
    }

    @Test
    void readRequestsAreNeverRecorded() {
        finish(request("GET", "deposits", walletId.toString()), 404);

        verifyNothingWasAudited();
    }

    @Test
    void anUnreadableWalletIdIsRecordedWithoutATarget() {
        finish(request("POST", "deposits", "not-a-uuid"), 404);

        verify(auditService).recordBestEffort(eq(actor), eq(AuditAction.REQUEST_REJECTED), isNull(), isNull(),
                isNull(), eq("deposits rejected with status 404"));
    }
}
