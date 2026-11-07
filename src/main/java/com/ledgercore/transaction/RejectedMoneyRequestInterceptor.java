package com.ledgercore.transaction;

import com.ledgercore.audit.AuditAction;
import com.ledgercore.audit.AuditService;
import com.ledgercore.audit.AuditTargetType;
import com.ledgercore.security.UserPrincipal;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.HandlerMapping;

import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Records money requests that were refused for a business reason. A refused request rolls back its own database
 * transaction, so it cannot write its own audit row; this runs after the response is final, outside any transaction.
 * Malformed input (400) and successes are not recorded. Only the operation, the wallet and the status are stored:
 * never the amount.
 */
@Component
public class RejectedMoneyRequestInterceptor implements HandlerInterceptor {

    /** 404: not found or not yours. 409: frozen wallet or key reuse. 422: insufficient funds or currency mismatch. */
    private static final Set<Integer> AUDITED_STATUSES = Set.of(404, 409, 422);

    private final AuditService auditService;

    public RejectedMoneyRequestInterceptor(AuditService auditService) {
        this.auditService = auditService;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        if (!"POST".equals(request.getMethod()) || !AUDITED_STATUSES.contains(response.getStatus())) {
            return;
        }
        UUID walletId = walletIdFrom(request);
        String uri = request.getRequestURI();
        String operation = uri.substring(uri.lastIndexOf('/') + 1);
        auditService.recordBestEffort(currentUserId(), AuditAction.REQUEST_REJECTED,
                walletId == null ? null : AuditTargetType.WALLET, walletId, null,
                operation + " rejected with status " + response.getStatus());
    }

    @SuppressWarnings("unchecked")
    private static UUID walletIdFrom(HttpServletRequest request) {
        Object variables = request.getAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE);
        if (variables instanceof Map<?, ?> map && map.get("walletId") instanceof String text) {
            try {
                return UUID.fromString(text);
            } catch (IllegalArgumentException e) {
                return null;
            }
        }
        return null;
    }

    private static UUID currentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof UserPrincipal principal) {
            return principal.getId();
        }
        return null;
    }
}
