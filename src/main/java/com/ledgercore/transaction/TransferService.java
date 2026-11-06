package com.ledgercore.transaction;

import com.ledgercore.common.CurrencyMismatchException;
import com.ledgercore.common.IdempotencyConflictException;
import com.ledgercore.common.InvalidAmountException;
import com.ledgercore.common.ResourceNotFoundException;
import com.ledgercore.common.SameWalletTransferException;
import com.ledgercore.transaction.dto.TransferResponse;
import com.ledgercore.wallet.Wallet;
import com.ledgercore.wallet.WalletRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

@Service
public class TransferService {

    private static final Logger log = LoggerFactory.getLogger(TransferService.class);

    private final WalletRepository walletRepository;
    private final WalletTransactionRepository transactionRepository;
    private final LedgerEntryRepository ledgerEntryRepository;

    public TransferService(WalletRepository walletRepository,
                           WalletTransactionRepository transactionRepository,
                           LedgerEntryRepository ledgerEntryRepository) {
        this.walletRepository = walletRepository;
        this.transactionRepository = transactionRepository;
        this.ledgerEntryRepository = ledgerEntryRepository;
    }

    @Transactional
    public TransferResponse transfer(UUID userId, UUID sourceWalletId, UUID destinationWalletId,
                                     BigDecimal amount, String idempotencyKey) {
        IdempotencyKeys.requireValid(idempotencyKey);
        if (amount == null) {
            throw new InvalidAmountException("Amount is required.");
        }
        if (sourceWalletId.equals(destinationWalletId)) {
            throw new SameWalletTransferException();
        }

        // 1. The source must belong to the caller. This check loads nothing, so no stale copy is cached.
        if (!walletRepository.existsByIdAndUserId(sourceWalletId, userId)) {
            throw new ResourceNotFoundException("Wallet not found.");
        }

        // 2. Lock both wallets, always the one with the lower id first. If A->B and B->A arrive together,
        //    both go for the same wallet first, so one simply waits instead of the two deadlocking.
        Wallet source;
        Wallet destination;
        if (sourceWalletId.compareTo(destinationWalletId) < 0) {
            source = lockSource(sourceWalletId);
            destination = lockDestination(destinationWalletId);
        } else {
            destination = lockDestination(destinationWalletId);
            source = lockSource(sourceWalletId);
        }

        if (source.getCurrency() != destination.getCurrency()) {
            throw new CurrencyMismatchException();
        }

        // 3. Only now, holding both locks, look for an earlier request with the same key (see TransactionService).
        String requestHash = RequestFingerprint.of(TransactionType.TRANSFER, sourceWalletId, destinationWalletId, amount);
        Optional<WalletTransaction> earlier = transactionRepository.findByUserIdAndIdempotencyKey(userId, idempotencyKey);
        if (earlier.isPresent()) {
            return replay(earlier.get(), requestHash);
        }

        // 4. Move the money. Any refusal throws, and the whole database transaction rolls back, so no half-transfer exists.
        source.debit(amount);
        destination.credit(amount);
        BigDecimal recordedAmount = amount.setScale(4);   // safe: both wallets already rejected anything finer

        WalletTransaction transaction = WalletTransaction.transfer(userId, sourceWalletId, destinationWalletId,
                source.getCurrency(), recordedAmount, idempotencyKey, requestHash);
        try {
            transactionRepository.saveAndFlush(transaction);
        } catch (DataIntegrityViolationException e) {
            throw new IdempotencyConflictException();
        }

        // 5. Two ledger entries of equal size: what leaves one wallet arrives in the other.
        ledgerEntryRepository.save(LedgerEntry.debit(transaction.getId(), sourceWalletId, recordedAmount, source.getBalance()));
        ledgerEntryRepository.save(LedgerEntry.credit(transaction.getId(), destinationWalletId, recordedAmount, destination.getBalance()));

        log.info("TRANSFER {} completed: {} {} from wallet id={} to wallet id={} for user id={}",
                transaction.getReference(), recordedAmount.toPlainString(), source.getCurrency(),
                sourceWalletId, destinationWalletId, userId);
        return TransferResponse.of(transaction, source.getBalance());
    }

    private Wallet lockSource(UUID walletId) {
        return walletRepository.findByIdForUpdate(walletId)
                .orElseThrow(() -> new ResourceNotFoundException("Wallet not found."));
    }

    private Wallet lockDestination(UUID walletId) {
        return walletRepository.findByIdForUpdate(walletId)
                .orElseThrow(() -> new ResourceNotFoundException("Destination wallet not found."));
    }

    /** The same key and the same request returns the original receipt; the same key and a different request is an error. */
    private TransferResponse replay(WalletTransaction earlier, String requestHash) {
        if (!earlier.getRequestHash().equals(requestHash)) {
            throw new IdempotencyConflictException();
        }
        BigDecimal senderBalanceAfter = ledgerEntryRepository.findByTransactionId(earlier.getId()).stream()
                .filter(entry -> entry.getDirection() == LedgerDirection.DEBIT)
                .findFirst()
                .map(LedgerEntry::getBalanceAfter)
                .orElseThrow(() -> new IllegalStateException("Ledger entry missing for " + earlier.getReference()));
        return TransferResponse.of(earlier, senderBalanceAfter);
    }
}
