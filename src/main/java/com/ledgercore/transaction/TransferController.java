package com.ledgercore.transaction;

import com.ledgercore.security.UserPrincipal;
import com.ledgercore.transaction.dto.TransferRequest;
import com.ledgercore.transaction.dto.TransferResponse;
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
public class TransferController {

    private final TransferService transferService;

    public TransferController(TransferService transferService) {
        this.transferService = transferService;
    }

    @PostMapping("/transfers")
    @ResponseStatus(HttpStatus.CREATED)
    public TransferResponse transfer(@AuthenticationPrincipal UserPrincipal principal,
                                     @PathVariable UUID walletId,
                                     @RequestHeader("Idempotency-Key") String idempotencyKey,
                                     @Valid @RequestBody TransferRequest request) {
        return transferService.transfer(principal.getId(), walletId, request.destinationWalletId(),
                request.amount(), idempotencyKey);
    }
}
