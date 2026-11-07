package com.ledgercore.transaction;

import com.ledgercore.common.PageResponse;
import com.ledgercore.security.UserPrincipal;
import com.ledgercore.transaction.dto.StatementEntryResponse;
import com.ledgercore.transaction.dto.TransactionDetailResponse;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
public class StatementController {

    private final StatementService statementService;

    public StatementController(StatementService statementService) {
        this.statementService = statementService;
    }

    @GetMapping("/wallets/{walletId}/transactions")
    public PageResponse<StatementEntryResponse> statement(@AuthenticationPrincipal UserPrincipal principal,
                                                          @PathVariable UUID walletId,
                                                          @RequestParam(required = false) TransactionType type,
                                                          @RequestParam(required = false) LedgerDirection direction,
                                                          @RequestParam(required = false) Instant from,
                                                          @RequestParam(required = false) Instant to,
                                                          @RequestParam(required = false) String reference,
                                                          @RequestParam(required = false) Integer page,
                                                          @RequestParam(required = false) Integer size) {
        StatementFilter filter = StatementFilter.of(type, direction, from, to, reference, page, size);
        return statementService.statement(principal.getId(), walletId, filter);
    }

    @GetMapping("/transactions/{reference}")
    public TransactionDetailResponse byReference(@AuthenticationPrincipal UserPrincipal principal,
                                                 @PathVariable String reference) {
        return statementService.findByReference(principal.getId(), reference);
    }
}
