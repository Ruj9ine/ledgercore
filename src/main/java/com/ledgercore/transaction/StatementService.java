package com.ledgercore.transaction;

import com.ledgercore.common.PageResponse;
import com.ledgercore.common.ResourceNotFoundException;
import com.ledgercore.transaction.dto.StatementEntryResponse;
import com.ledgercore.transaction.dto.TransactionDetailResponse;
import com.ledgercore.wallet.WalletRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;
import java.util.UUID;

@Service
public class StatementService {

    private final WalletRepository walletRepository;
    private final LedgerEntryRepository ledgerEntryRepository;
    private final WalletTransactionRepository transactionRepository;

    public StatementService(WalletRepository walletRepository,
                            LedgerEntryRepository ledgerEntryRepository,
                            WalletTransactionRepository transactionRepository) {
        this.walletRepository = walletRepository;
        this.ledgerEntryRepository = ledgerEntryRepository;
        this.transactionRepository = transactionRepository;
    }

    // The mapping to DTOs happens inside this transaction on purpose: open-in-view is off, so each entry's
    // lazily loaded transaction can only be read while the session is still open.
    @Transactional(readOnly = true)
    public PageResponse<StatementEntryResponse> statement(UUID userId, UUID walletId, StatementFilter filter) {
        if (!walletRepository.existsByIdAndUserId(walletId, userId)) {
            throw new ResourceNotFoundException("Wallet not found.");
        }
        // Newest first. The id breaks ties between entries with the same timestamp, so a row can never
        // appear on two pages or on none while someone pages through.
        Pageable pageable = PageRequest.of(filter.page(), filter.size(),
                Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id")));
        Page<LedgerEntry> entries = ledgerEntryRepository.findAll(
                StatementSpecifications.forWallet(walletId, filter), pageable);
        return PageResponse.from(entries.map(StatementEntryResponse::from));
    }

    /** A transaction is visible only to the owner of its source or destination wallet; anyone else gets "not found". */
    @Transactional(readOnly = true)
    public TransactionDetailResponse findByReference(UUID userId, String reference) {
        String normalized = reference.trim().toUpperCase(Locale.ROOT);
        return transactionRepository.findByReference(normalized)
                .filter(transaction -> isParty(userId, transaction))
                .map(TransactionDetailResponse::from)
                .orElseThrow(() -> new ResourceNotFoundException("Transaction not found."));
    }

    private boolean isParty(UUID userId, WalletTransaction transaction) {
        return (transaction.getSourceWalletId() != null
                    && walletRepository.existsByIdAndUserId(transaction.getSourceWalletId(), userId))
                || (transaction.getDestinationWalletId() != null
                    && walletRepository.existsByIdAndUserId(transaction.getDestinationWalletId(), userId));
    }
}
