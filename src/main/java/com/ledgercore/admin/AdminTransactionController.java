package com.ledgercore.admin;

import com.ledgercore.admin.dto.AdminTransactionResponse;
import com.ledgercore.common.PageResponse;
import com.ledgercore.transaction.TransactionType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin/transactions")
@PreAuthorize("hasRole('ADMIN')")
public class AdminTransactionController {

    private final AdminTransactionService adminTransactionService;

    public AdminTransactionController(AdminTransactionService adminTransactionService) {
        this.adminTransactionService = adminTransactionService;
    }

    @GetMapping
    public PageResponse<AdminTransactionResponse> list(@RequestParam(required = false) TransactionType type,
                                                       @RequestParam(required = false) UUID walletId,
                                                       @RequestParam(required = false) UUID userId,
                                                       @RequestParam(required = false) Instant from,
                                                       @RequestParam(required = false) Instant to,
                                                       @RequestParam(required = false) String reference,
                                                       @RequestParam(required = false) Integer page,
                                                       @RequestParam(required = false) Integer size) {
        return adminTransactionService.list(
                AdminTransactionFilter.of(type, walletId, userId, from, to, reference, page, size));
    }
}
