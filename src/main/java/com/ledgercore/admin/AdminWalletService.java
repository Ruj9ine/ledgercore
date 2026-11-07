package com.ledgercore.admin;

import com.ledgercore.admin.dto.AdminWalletResponse;
import com.ledgercore.audit.AuditAction;
import com.ledgercore.audit.AuditService;
import com.ledgercore.audit.AuditTargetType;
import com.ledgercore.common.PageResponse;
import com.ledgercore.common.ResourceNotFoundException;
import com.ledgercore.common.StateConflictException;
import com.ledgercore.wallet.Wallet;
import com.ledgercore.wallet.WalletRepository;
import com.ledgercore.wallet.WalletStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class AdminWalletService {

    private final WalletRepository walletRepository;
    private final AuditService auditService;

    public AdminWalletService(WalletRepository walletRepository, AuditService auditService) {
        this.walletRepository = walletRepository;
        this.auditService = auditService;
    }

    @Transactional(readOnly = true)
    public PageResponse<AdminWalletResponse> list(WalletFilter filter) {
        Pageable pageable = PageRequest.of(filter.page(), filter.size(),
                Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id")));
        Page<Wallet> wallets = walletRepository.findAll(WalletSpecifications.matching(filter), pageable);
        return PageResponse.from(wallets.map(AdminWalletResponse::from));
    }

    @Transactional(readOnly = true)
    public AdminWalletResponse get(UUID walletId) {
        return walletRepository.findById(walletId)
                .map(AdminWalletResponse::from)
                .orElseThrow(() -> new ResourceNotFoundException("Wallet not found."));
    }

    @Transactional
    public AdminWalletResponse freeze(UUID actorId, UUID walletId, String reason) {
        // The row lock is the same one transfers take, so a freeze can never interleave with a movement in flight.
        Wallet wallet = lock(walletId);
        if (wallet.getStatus() == WalletStatus.FROZEN) {
            throw new StateConflictException("This wallet is already frozen.");
        }
        wallet.freeze();
        walletRepository.saveAndFlush(wallet);
        auditService.record(actorId, AuditAction.WALLET_FROZEN, AuditTargetType.WALLET, walletId, reason.trim(), "was ACTIVE");
        return AdminWalletResponse.from(wallet);
    }

    @Transactional
    public AdminWalletResponse unfreeze(UUID actorId, UUID walletId, String reason) {
        Wallet wallet = lock(walletId);
        if (wallet.getStatus() == WalletStatus.ACTIVE) {
            throw new StateConflictException("This wallet is not frozen.");
        }
        wallet.activate();
        walletRepository.saveAndFlush(wallet);
        auditService.record(actorId, AuditAction.WALLET_UNFROZEN, AuditTargetType.WALLET, walletId, reason.trim(), "was FROZEN");
        return AdminWalletResponse.from(wallet);
    }

    private Wallet lock(UUID walletId) {
        return walletRepository.findByIdForUpdate(walletId)
                .orElseThrow(() -> new ResourceNotFoundException("Wallet not found."));
    }
}
