package com.ledgercore.transaction;

import com.ledgercore.common.CurrencyMismatchException;
import com.ledgercore.common.IdempotencyConflictException;
import com.ledgercore.common.InsufficientFundsException;
import com.ledgercore.common.InvalidIdempotencyKeyException;
import com.ledgercore.common.ResourceNotFoundException;
import com.ledgercore.common.SameWalletTransferException;
import com.ledgercore.transaction.dto.TransferResponse;
import com.ledgercore.wallet.SupportedCurrency;
import com.ledgercore.wallet.Wallet;
import com.ledgercore.wallet.WalletRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TransferServiceTest {

    private static final String KEY = "transfer-1";

    @Mock private WalletRepository walletRepository;
    @Mock private WalletTransactionRepository transactionRepository;
    @Mock private LedgerEntryRepository ledgerEntryRepository;

    @InjectMocks private TransferService service;

    private final UUID userId = UUID.randomUUID();
    // Two ids with a known order, so the tests can say which wallet must be locked first.
    private final UUID lowId = new UUID(0L, 1L);
    private final UUID highId = new UUID(0L, 2L);

    private Wallet wallet(SupportedCurrency currency, String balance) {
        Wallet wallet = new Wallet(UUID.randomUUID(), currency);
        if (balance != null) {
            wallet.credit(new BigDecimal(balance));
        }
        return wallet;
    }

    private void givenOwnedSource(UUID sourceId) {
        when(walletRepository.existsByIdAndUserId(sourceId, userId)).thenReturn(true);
    }

    private void givenLockable(UUID id, Wallet wallet) {
        when(walletRepository.findByIdForUpdate(id)).thenReturn(Optional.of(wallet));
    }

    private void givenNoEarlierRequest() {
        when(transactionRepository.findByUserIdAndIdempotencyKey(userId, KEY)).thenReturn(Optional.empty());
    }

    private void givenSavesWork() {
        when(transactionRepository.saveAndFlush(any(WalletTransaction.class))).thenAnswer(i -> i.getArgument(0));
    }

    @Test
    void transferMovesMoneyAndWritesOneDebitAndOneCreditOfTheSameAmount() {
        Wallet source = wallet(SupportedCurrency.USD, "100.00");
        Wallet destination = wallet(SupportedCurrency.USD, null);
        givenOwnedSource(lowId);
        givenLockable(lowId, source);
        givenLockable(highId, destination);
        givenNoEarlierRequest();
        givenSavesWork();

        TransferResponse response = service.transfer(userId, lowId, highId, new BigDecimal("30.00"), KEY);

        ArgumentCaptor<LedgerEntry> entries = ArgumentCaptor.forClass(LedgerEntry.class);
        verify(ledgerEntryRepository, times(2)).save(entries.capture());
        LedgerEntry debit = entries.getAllValues().get(0);
        LedgerEntry credit = entries.getAllValues().get(1);
        assertThat(debit.getDirection()).isEqualTo(LedgerDirection.DEBIT);
        assertThat(debit.getWalletId()).isEqualTo(lowId);
        assertThat(debit.getBalanceAfter()).isEqualByComparingTo("70.00");
        assertThat(credit.getDirection()).isEqualTo(LedgerDirection.CREDIT);
        assertThat(credit.getWalletId()).isEqualTo(highId);
        assertThat(credit.getBalanceAfter()).isEqualByComparingTo("30.00");
        assertThat(debit.getAmount()).isEqualByComparingTo(credit.getAmount());
        assertThat(source.getBalance()).isEqualByComparingTo("70.00");
        assertThat(destination.getBalance()).isEqualByComparingTo("30.00");
        assertThat(response.balanceAfter()).isEqualTo("70.0000");
        assertThat(response.type()).isEqualTo(TransactionType.TRANSFER);
    }

    @Test
    void locksTheLowerIdFirstWhenTheSourceHasTheLowerId() {
        givenOwnedSource(lowId);
        givenLockable(lowId, wallet(SupportedCurrency.USD, "100.00"));
        givenLockable(highId, wallet(SupportedCurrency.USD, null));
        givenNoEarlierRequest();
        givenSavesWork();

        service.transfer(userId, lowId, highId, BigDecimal.TEN, KEY);

        InOrder order = inOrder(walletRepository);
        order.verify(walletRepository).findByIdForUpdate(lowId);
        order.verify(walletRepository).findByIdForUpdate(highId);
    }

    @Test
    void locksTheLowerIdFirstEvenWhenItIsTheDestination() {
        givenOwnedSource(highId);
        givenLockable(highId, wallet(SupportedCurrency.USD, "100.00"));
        givenLockable(lowId, wallet(SupportedCurrency.USD, null));
        givenNoEarlierRequest();
        givenSavesWork();

        service.transfer(userId, highId, lowId, BigDecimal.TEN, KEY);

        InOrder order = inOrder(walletRepository);
        order.verify(walletRepository).findByIdForUpdate(lowId);
        order.verify(walletRepository).findByIdForUpdate(highId);
    }

    @Test
    void transferToTheSameWalletIsRejected() {
        assertThatThrownBy(() -> service.transfer(userId, lowId, lowId, BigDecimal.TEN, KEY))
                .isInstanceOf(SameWalletTransferException.class);
    }

    @Test
    void currencyMismatchIsRejectedBeforeAnyMoneyMoves() {
        Wallet source = wallet(SupportedCurrency.USD, "100.00");
        givenOwnedSource(lowId);
        givenLockable(lowId, source);
        givenLockable(highId, wallet(SupportedCurrency.UGX, null));

        assertThatThrownBy(() -> service.transfer(userId, lowId, highId, BigDecimal.TEN, KEY))
                .isInstanceOf(CurrencyMismatchException.class);

        verify(transactionRepository, never()).saveAndFlush(any(WalletTransaction.class));
        assertThat(source.getBalance()).isEqualByComparingTo("100.00");
    }

    @Test
    void insufficientFundsRecordsNothing() {
        Wallet source = wallet(SupportedCurrency.USD, "50.00");
        Wallet destination = wallet(SupportedCurrency.USD, null);
        givenOwnedSource(lowId);
        givenLockable(lowId, source);
        givenLockable(highId, destination);
        givenNoEarlierRequest();

        assertThatThrownBy(() -> service.transfer(userId, lowId, highId, new BigDecimal("80.00"), KEY))
                .isInstanceOf(InsufficientFundsException.class);

        verify(transactionRepository, never()).saveAndFlush(any(WalletTransaction.class));
        verify(ledgerEntryRepository, never()).save(any(LedgerEntry.class));
        assertThat(source.getBalance()).isEqualByComparingTo("50.00");
        assertThat(destination.getBalance()).isEqualByComparingTo("0");
    }

    @Test
    void aMissingDestinationIsReportedAsNotFound() {
        givenOwnedSource(lowId);
        givenLockable(lowId, wallet(SupportedCurrency.USD, "100.00"));
        when(walletRepository.findByIdForUpdate(highId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.transfer(userId, lowId, highId, BigDecimal.TEN, KEY))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Destination");
    }

    @Test
    void aSourceTheCallerDoesNotOwnIsNotFoundAndNothingIsLocked() {
        when(walletRepository.existsByIdAndUserId(lowId, userId)).thenReturn(false);

        assertThatThrownBy(() -> service.transfer(userId, lowId, highId, BigDecimal.TEN, KEY))
                .isInstanceOf(ResourceNotFoundException.class);

        verify(walletRepository, never()).findByIdForUpdate(any());
    }

    @Test
    void sameKeyAndSameRequestReturnsTheOriginalReceiptAndMovesNothing() {
        BigDecimal amount = new BigDecimal("30.00");
        String hash = RequestFingerprint.of(TransactionType.TRANSFER, lowId, highId, amount);
        WalletTransaction original = WalletTransaction.transfer(userId, lowId, highId, SupportedCurrency.USD,
                new BigDecimal("30.0000"), KEY, hash);
        Wallet source = wallet(SupportedCurrency.USD, "100.00");
        givenOwnedSource(lowId);
        givenLockable(lowId, source);
        givenLockable(highId, wallet(SupportedCurrency.USD, null));
        when(transactionRepository.findByUserIdAndIdempotencyKey(userId, KEY)).thenReturn(Optional.of(original));
        when(ledgerEntryRepository.findByTransactionId(any())).thenReturn(List.of(
                LedgerEntry.credit(null, highId, new BigDecimal("30.0000"), new BigDecimal("30.0000")),
                LedgerEntry.debit(null, lowId, new BigDecimal("30.0000"), new BigDecimal("70.0000"))));

        TransferResponse response = service.transfer(userId, lowId, highId, amount, KEY);

        assertThat(response.reference()).isEqualTo(original.getReference());
        assertThat(response.balanceAfter()).isEqualTo("70.0000");   // the sender's balance at the time, not now
        verify(transactionRepository, never()).saveAndFlush(any(WalletTransaction.class));
        verify(ledgerEntryRepository, never()).save(any(LedgerEntry.class));
        assertThat(source.getBalance()).isEqualByComparingTo("100.00");
    }

    @Test
    void sameKeyWithADifferentAmountIsRejected() {
        String hashOfOriginal = RequestFingerprint.of(TransactionType.TRANSFER, lowId, highId, new BigDecimal("30"));
        WalletTransaction original = WalletTransaction.transfer(userId, lowId, highId, SupportedCurrency.USD,
                new BigDecimal("30.0000"), KEY, hashOfOriginal);
        givenOwnedSource(lowId);
        givenLockable(lowId, wallet(SupportedCurrency.USD, "100.00"));
        givenLockable(highId, wallet(SupportedCurrency.USD, null));
        when(transactionRepository.findByUserIdAndIdempotencyKey(userId, KEY)).thenReturn(Optional.of(original));

        assertThatThrownBy(() -> service.transfer(userId, lowId, highId, new BigDecimal("40"), KEY))
                .isInstanceOf(IdempotencyConflictException.class);
    }

    @Test
    void aBlankKeyIsRejectedBeforeAnythingElseHappens() {
        assertThatThrownBy(() -> service.transfer(userId, lowId, highId, BigDecimal.TEN, " "))
                .isInstanceOf(InvalidIdempotencyKeyException.class);
    }
}