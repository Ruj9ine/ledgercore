package com.ledgercore.transaction;

import com.ledgercore.common.IdempotencyConflictException;
import com.ledgercore.common.InvalidAmountException;
import com.ledgercore.common.ResourceNotFoundException;
import com.ledgercore.transaction.dto.TransactionResponse;
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
public class TransactionService {

    private static final Logger log = LoggerFactory.getLogger(TransactionService.class);

    private final WalletRepository walletRepository;
    private final WalletTransactionRepository transactionRepository;
    private final LedgerEntryRepository ledgerEntryRepository;

    public TransactionService(WalletRepository walletRepository,
                              WalletTransactionRepository transactionRepository,
                              LedgerEntryRepository ledgerEntryRepository) {
        this.walletRepository = walletRepository;
        this.transactionRepository = transactionRepository;
        this.ledgerEntryRepository = ledgerEntryRepository;
    }

    @Transactional
    public TransactionResponse deposit(UUID userId, UUID walletId, BigDecimal amount, String idempotencyKey) {
        return execute(TransactionType.DEPOSIT, userId, walletId, amount, idempotencyKey);
    }

    @Transactional
    public TransactionResponse withdraw(UUID userId, UUID walletId, BigDecimal amount, String idempotencyKey) {
        return execute(TransactionType.WITHDRAWAL, userId, walletId, amount, idempotencyKey);
    }

    private TransactionResponse execute(TransactionType type, UUID userId, UUID walletId,
                                        BigDecimal amount, String idempotencyKey) {
            IdempotencyKeys.requireValid(idempotencyKey);
        
        if (amount == null) {
            throw new InvalidAmountException("Amount is required.");
        }

        // 1. Lock the wallet row first. Every request for this wallet queues here, one at a time.
        Wallet wallet = walletRepository.findByIdAndUserIdForUpdate(walletId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Wallet not found."));

        // 2. Only now look for an earlier request with the same key. Because of the lock, a request that
        //    started at the same moment as ours has already committed, so we are guaranteed to see it.
        String requestHash = RequestFingerprint.of(type, walletId, amount);
        Optional<WalletTransaction> earlier = transactionRepository.findByUserIdAndIdempotencyKey(userId, idempotencyKey);
        if (earlier.isPresent()) {
            return replay(earlier.get(), requestHash, walletId);
        }

        // 3. Apply the money rules. A refusal throws before anything is saved, and the whole transaction rolls back.
        if (type == TransactionType.DEPOSIT) {
            wallet.credit(amount);
        } else {
            wallet.debit(amount);
        }
        BigDecimal recordedAmount = amount.setScale(4);   // safe: the wallet already rejected anything finer

        // 4. Record the transaction and its ledger entry in the same database transaction as the balance change.
        WalletTransaction transaction = type == TransactionType.DEPOSIT
                ? WalletTransaction.deposit(userId, walletId, wallet.getCurrency(), recordedAmount, idempotencyKey, requestHash)
                : WalletTransaction.withdrawal(userId, walletId, wallet.getCurrency(), recordedAmount, idempotencyKey, requestHash);
        try {
            transactionRepository.saveAndFlush(transaction);
        } catch (DataIntegrityViolationException e) {
            // The same key was used for a different wallet at the same moment, so the wallet lock did not serialize us.
            throw new IdempotencyConflictException();
        }

        LedgerEntry entry = type == TransactionType.DEPOSIT
                ? LedgerEntry.credit(transaction.getId(), walletId, recordedAmount, wallet.getBalance())
                : LedgerEntry.debit(transaction.getId(), walletId, recordedAmount, wallet.getBalance());
        ledgerEntryRepository.save(entry);

        log.info("{} {} completed: {} {} on wallet id={} for user id={}",
                type, transaction.getReference(), recordedAmount.toPlainString(), wallet.getCurrency(), walletId, userId);
        return TransactionResponse.of(transaction, walletId, wallet.getBalance());
    }

    /** The same key and the same request returns the original receipt; the same key and a different request is an error. */
    private TransactionResponse replay(WalletTransaction earlier, String requestHash, UUID walletId) {
        if (!earlier.getRequestHash().equals(requestHash)) {
            throw new IdempotencyConflictException();
        }
        BigDecimal balanceAfter = ledgerEntryRepository.findByTransactionId(earlier.getId()).stream()
                .findFirst()
                .map(LedgerEntry::getBalanceAfter)
                .orElseThrow(() -> new IllegalStateException("Ledger entry missing for " + earlier.getReference()));
        return TransactionResponse.of(earlier, walletId, balanceAfter);
    }
}
