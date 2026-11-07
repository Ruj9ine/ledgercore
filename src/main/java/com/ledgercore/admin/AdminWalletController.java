package com.ledgercore.admin;

import com.ledgercore.admin.dto.AdminActionRequest;
import com.ledgercore.admin.dto.AdminWalletResponse;
import com.ledgercore.common.PageResponse;
import com.ledgercore.security.UserPrincipal;
import com.ledgercore.wallet.SupportedCurrency;
import com.ledgercore.wallet.WalletStatus;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin/wallets")
@PreAuthorize("hasRole('ADMIN')")   // a second lock behind the URL rule in SecurityConfig
public class AdminWalletController {

    private final AdminWalletService adminWalletService;

    public AdminWalletController(AdminWalletService adminWalletService) {
        this.adminWalletService = adminWalletService;
    }

    @GetMapping
    public PageResponse<AdminWalletResponse> list(@RequestParam(required = false) UUID userId,
                                                  @RequestParam(required = false) SupportedCurrency currency,
                                                  @RequestParam(required = false) WalletStatus status,
                                                  @RequestParam(required = false) Integer page,
                                                  @RequestParam(required = false) Integer size) {
        return adminWalletService.list(WalletFilter.of(userId, currency, status, page, size));
    }

    @GetMapping("/{id}")
    public AdminWalletResponse get(@PathVariable UUID id) {
        return adminWalletService.get(id);
    }

    @PostMapping("/{id}/freeze")
    public AdminWalletResponse freeze(@AuthenticationPrincipal UserPrincipal principal,
                                      @PathVariable UUID id,
                                      @Valid @RequestBody AdminActionRequest request) {
        return adminWalletService.freeze(principal.getId(), id, request.reason());
    }

    @PostMapping("/{id}/unfreeze")
    public AdminWalletResponse unfreeze(@AuthenticationPrincipal UserPrincipal principal,
                                        @PathVariable UUID id,
                                        @Valid @RequestBody AdminActionRequest request) {
        return adminWalletService.unfreeze(principal.getId(), id, request.reason());
    }
}
