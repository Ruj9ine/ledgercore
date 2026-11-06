package com.ledgercore.transaction;

import com.ledgercore.security.UserPrincipal;
import com.ledgercore.transaction.dto.AmountRequest;
import com.ledgercore.transaction.dto.TransactionResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/wallets/{walletId}")
public class TransactionController {

    private final TransactionService transactionService;

    public TransactionController(TransactionService transactionService) {
        this.transactionService = transactionService;
    }

    @PostMapping("/deposits")
    @ResponseStatus(HttpStatus.CREATED)
    public TransactionResponse deposit(@AuthenticationPrincipal UserPrincipal principal,
                                       @PathVariable UUID walletId,
                                       @RequestHeader("Idempotency-Key") String idempotencyKey,
                                       @Valid @RequestBody AmountRequest request) {
        return transactionService.deposit(principal.getId(), walletId, request.amount(), idempotencyKey);
    }

    @PostMapping("/withdrawals")
    @ResponseStatus(HttpStatus.CREATED)
    public TransactionResponse withdraw(@AuthenticationPrincipal UserPrincipal principal,
                                        @PathVariable UUID walletId,
                                        @RequestHeader("Idempotency-Key") String idempotencyKey,
                                        @Valid @RequestBody AmountRequest request) {
        return transactionService.withdraw(principal.getId(), walletId, request.amount(), idempotencyKey);
    }
}