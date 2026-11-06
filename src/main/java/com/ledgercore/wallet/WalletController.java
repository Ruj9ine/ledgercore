package com.ledgercore.wallet;

import com.ledgercore.security.UserPrincipal;
import com.ledgercore.wallet.dto.CreateWalletRequest;
import com.ledgercore.wallet.dto.WalletResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/wallets")
public class WalletController {

    private final WalletService walletService;

    public WalletController(WalletService walletService) {
        this.walletService = walletService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public WalletResponse create(@AuthenticationPrincipal UserPrincipal principal,
                                 @Valid @RequestBody CreateWalletRequest request) {
        return walletService.create(principal.getId(), request.currency());
    }

    @GetMapping
    public List<WalletResponse> list(@AuthenticationPrincipal UserPrincipal principal) {
        return walletService.listFor(principal.getId());
    }

    @GetMapping("/{id}")
    public WalletResponse get(@AuthenticationPrincipal UserPrincipal principal, @PathVariable UUID id) {
        return walletService.getOwned(id, principal.getId());
    }
}