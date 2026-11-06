package com.ledgercore.wallet;

import com.ledgercore.common.ResourceNotFoundException;
import com.ledgercore.common.WalletAlreadyExistsException;
import com.ledgercore.wallet.dto.WalletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class WalletService {

    private static final Logger log = LoggerFactory.getLogger(WalletService.class);

    private final WalletRepository walletRepository;

    public WalletService(WalletRepository walletRepository) {
        this.walletRepository = walletRepository;
    }

    @Transactional
    public WalletResponse create(UUID userId, SupportedCurrency currency) {
        if (walletRepository.existsByUserIdAndCurrency(userId, currency)) {
            throw new WalletAlreadyExistsException(currency.name());
        }
        Wallet wallet = new Wallet(userId, currency);
        try {
            walletRepository.saveAndFlush(wallet);
        } catch (DataIntegrityViolationException e) {
            // Two concurrent requests passed the check above; the unique constraint decides.
            throw new WalletAlreadyExistsException(currency.name());
        }
        log.info("Created {} wallet id={} for user id={}", currency, wallet.getId(), userId);
        return WalletResponse.from(wallet);
    }

    @Transactional(readOnly = true)
    public List<WalletResponse> listFor(UUID userId) {
        return walletRepository.findByUserIdOrderByCreatedAtAsc(userId).stream()
                .map(WalletResponse::from)
                .toList();
    }

    /** Returns the wallet only if it belongs to the caller; someone else's wallet is reported as not found. */
    @Transactional(readOnly = true)
    public WalletResponse getOwned(UUID walletId, UUID userId) {
        return walletRepository.findByIdAndUserId(walletId, userId)
                .map(WalletResponse::from)
                .orElseThrow(() -> new ResourceNotFoundException("Wallet not found."));
    }
}
