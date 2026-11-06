package com.ledgercore.transaction;

import com.ledgercore.common.IdempotencyConflictException;
import com.ledgercore.common.InsufficientFundsException;
import com.ledgercore.common.InvalidIdempotencyKeyException;
import com.ledgercore.common.ResourceNotFoundException;
import com.ledgercore.transaction.dto.TransactionResponse;
import com.ledgercore.wallet.SupportedCurrency;
import com.ledgercore.wallet.Wallet;
import com.ledgercore.wallet.WalletRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TransactionServiceTest {

    private static final String KEY = "key-1";

    @Mock private WalletRepository walletRepository;
    @Mock private WalletTransactionRepository transactionRepository;
    @Mock private LedgerEntryRepository ledgerEntryRepository;

    @InjectMocks private TransactionService service;

    private final UUID userId = UUID.randomUUID();
    private final UUID walletId = UUID.randomUUID();

    private Wallet usdWalletWithBalance(String balance) {
        Wallet wallet = new Wallet(userId, SupportedCurrency.USD);
        if (balance != null) {
            wallet.credit(new BigDecimal(balance));
        }
        return wallet;
    }

    private void givenWallet(Wallet wallet) {
        when(walletRepository.findByIdAndUserIdForUpdate(walletId, userId)).thenReturn(Optional.of(wallet));
    }

    private void givenNoEarlierRequest() {
        when(transactionRepository.findByUserIdAndIdempotencyKey(userId, KEY)).thenReturn(Optional.empty());
        when(transactionRepository.saveAndFlush(any(WalletTransaction.class))).thenAnswer(i -> i.getArgument(0));
    }

    @Test
    void depositCreditsTheWalletAndWritesACreditLedgerEntry() {
        Wallet wallet = usdWalletWithBalance(null);
        givenWallet(wallet);
        givenNoEarlierRequest();

        TransactionResponse response = service.deposit(userId, walletId, new BigDecimal("100.50"), KEY);

        ArgumentCaptor<LedgerEntry> entry = ArgumentCaptor.forClass(LedgerEntry.class);
        verify(ledgerEntryRepository).save(entry.capture());
        assertThat(entry.getValue().getDirection()).isEqualTo(LedgerDirection.CREDIT);
        assertThat(entry.getValue().getAmount()).isEqualByComparingTo("100.50");
        assertThat(entry.getValue().getBalanceAfter()).isEqualByComparingTo("100.50");
        assertThat(wallet.getBalance()).isEqualByComparingTo("100.50");
        assertThat(response.type()).isEqualTo(TransactionType.DEPOSIT);
        assertThat(response.balanceAfter()).isEqualTo("100.5000");
    }

    @Test
    void withdrawalDebitsTheWalletAndWritesADebitLedgerEntry() {
        Wallet wallet = usdWalletWithBalance("100.00");
        givenWallet(wallet);
        givenNoEarlierRequest();

        TransactionResponse response = service.withdraw(userId, walletId, new BigDecimal("30.00"), KEY);

        ArgumentCaptor<LedgerEntry> entry = ArgumentCaptor.forClass(LedgerEntry.class);
        verify(ledgerEntryRepository).save(entry.capture());
        assertThat(entry.getValue().getDirection()).isEqualTo(LedgerDirection.DEBIT);
        assertThat(entry.getValue().getBalanceAfter()).isEqualByComparingTo("70.00");
        assertThat(response.type()).isEqualTo(TransactionType.WITHDRAWAL);
        assertThat(response.balanceAfter()).isEqualTo("70.0000");
    }

    @Test
    void overdraftIsRefusedAndNothingIsRecorded() {
        Wallet wallet = usdWalletWithBalance("50.00");
        givenWallet(wallet);
        when(transactionRepository.findByUserIdAndIdempotencyKey(userId, KEY)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.withdraw(userId, walletId, new BigDecimal("80.00"), KEY))
                .isInstanceOf(InsufficientFundsException.class);

        verify(transactionRepository, never()).saveAndFlush(any(WalletTransaction.class));
        verify(ledgerEntryRepository, never()).save(any(LedgerEntry.class));
        assertThat(wallet.getBalance()).isEqualByComparingTo("50.00");
    }

    @Test
    void sameKeyAndSameRequestReturnsTheOriginalReceiptWithoutTouchingTheWallet() {
        BigDecimal amount = new BigDecimal("100.00");
        String hash = RequestFingerprint.of(TransactionType.DEPOSIT, walletId, amount);
        WalletTransaction original = WalletTransaction.deposit(userId, walletId, SupportedCurrency.USD,
                new BigDecimal("100.0000"), KEY, hash);
        Wallet wallet = usdWalletWithBalance("100.00");
        givenWallet(wallet);
        when(transactionRepository.findByUserIdAndIdempotencyKey(userId, KEY)).thenReturn(Optional.of(original));
        when(ledgerEntryRepository.findByTransactionId(any())).thenReturn(List.of(
                LedgerEntry.credit(null, walletId, new BigDecimal("100.0000"), new BigDecimal("100.0000"))));

        TransactionResponse response = service.deposit(userId, walletId, amount, KEY);

        assertThat(response.reference()).isEqualTo(original.getReference());
        assertThat(response.balanceAfter()).isEqualTo("100.0000");
        verify(transactionRepository, never()).saveAndFlush(any(WalletTransaction.class));
        verify(ledgerEntryRepository, never()).save(any(LedgerEntry.class));
        assertThat(wallet.getBalance()).isEqualByComparingTo("100.00");
    }

    @Test
    void sameKeyWithADifferentAmountIsRejected() {
        String hashOfOriginal = RequestFingerprint.of(TransactionType.DEPOSIT, walletId, new BigDecimal("100"));
        WalletTransaction original = WalletTransaction.deposit(userId, walletId, SupportedCurrency.USD,
                new BigDecimal("100.0000"), KEY, hashOfOriginal);
        givenWallet(usdWalletWithBalance("100.00"));
        when(transactionRepository.findByUserIdAndIdempotencyKey(userId, KEY)).thenReturn(Optional.of(original));

        assertThatThrownBy(() -> service.deposit(userId, walletId, new BigDecimal("200"), KEY))
                .isInstanceOf(IdempotencyConflictException.class);
    }

    @Test
    void aUniqueConstraintRaceOnTheKeyBecomesAConflict() {
        givenWallet(usdWalletWithBalance(null));
        when(transactionRepository.findByUserIdAndIdempotencyKey(userId, KEY)).thenReturn(Optional.empty());
        when(transactionRepository.saveAndFlush(any(WalletTransaction.class)))
                .thenThrow(new DataIntegrityViolationException("duplicate key"));

        assertThatThrownBy(() -> service.deposit(userId, walletId, BigDecimal.TEN, KEY))
                .isInstanceOf(IdempotencyConflictException.class);
    }

    @Test
    void aBlankKeyIsRejectedBeforeAnythingElseHappens() {
        assertThatThrownBy(() -> service.deposit(userId, walletId, BigDecimal.TEN, " "))
                .isInstanceOf(InvalidIdempotencyKeyException.class);
    }

    @Test
    void anotherUsersWalletIsReportedAsNotFound() {
        when(walletRepository.findByIdAndUserIdForUpdate(walletId, userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.deposit(userId, walletId, BigDecimal.TEN, KEY))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
