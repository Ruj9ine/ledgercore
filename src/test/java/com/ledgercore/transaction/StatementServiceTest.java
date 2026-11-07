package com.ledgercore.transaction;

import com.ledgercore.common.PageResponse;
import com.ledgercore.common.ResourceNotFoundException;
import com.ledgercore.transaction.dto.StatementEntryResponse;
import com.ledgercore.transaction.dto.TransactionDetailResponse;
import com.ledgercore.wallet.SupportedCurrency;
import com.ledgercore.wallet.WalletRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.test.util.ReflectionTestUtils;

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
class StatementServiceTest {

    @Mock private WalletRepository walletRepository;
    @Mock private LedgerEntryRepository ledgerEntryRepository;
    @Mock private WalletTransactionRepository transactionRepository;

    @InjectMocks private StatementService service;

    private final UUID userId = UUID.randomUUID();
    private final UUID walletId = UUID.randomUUID();

    private StatementFilter noFilters(Integer page, Integer size) {
        return StatementFilter.of(null, null, null, null, null, page, size);
    }

    private WalletTransaction deposit() {
        return WalletTransaction.deposit(userId, walletId, SupportedCurrency.USD, new BigDecimal("10.0000"), "k", "h");
    }

    @Test
    void aWalletTheCallerDoesNotOwnIsNotFoundAndNothingIsQueried() {
        when(walletRepository.existsByIdAndUserId(walletId, userId)).thenReturn(false);

        assertThatThrownBy(() -> service.statement(userId, walletId, noFilters(null, null)))
                .isInstanceOf(ResourceNotFoundException.class);

        verify(ledgerEntryRepository, never()).findAll(any(Specification.class), any(Pageable.class));
    }

    @Test
    void theStatementIsNewestFirstWithAnIdTieBreakerAndUsesTheRequestedPage() {
        when(walletRepository.existsByIdAndUserId(walletId, userId)).thenReturn(true);
        when(ledgerEntryRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<LedgerEntry>(List.of()));

        service.statement(userId, walletId, noFilters(2, 25));

        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(ledgerEntryRepository).findAll(any(Specification.class), pageable.capture());
        assertThat(pageable.getValue().getPageNumber()).isEqualTo(2);
        assertThat(pageable.getValue().getPageSize()).isEqualTo(25);
        Sort sort = pageable.getValue().getSort();
        assertThat(sort.stream().map(Sort.Order::getProperty)).containsExactly("createdAt", "id");
        assertThat(sort.getOrderFor("createdAt").isDescending()).isTrue();
        assertThat(sort.getOrderFor("id").isDescending()).isTrue();
    }

    @Test
    void entriesAreMappedIntoOurOwnPageShape() {
        WalletTransaction deposit = deposit();
        LedgerEntry entry = LedgerEntry.credit(null, walletId, new BigDecimal("10.0000"), new BigDecimal("10.0000"));
        ReflectionTestUtils.setField(entry, "transaction", deposit);
        when(walletRepository.existsByIdAndUserId(walletId, userId)).thenReturn(true);
        when(ledgerEntryRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<LedgerEntry>(List.of(entry), PageRequest.of(0, 20), 1));

        PageResponse<StatementEntryResponse> response = service.statement(userId, walletId, noFilters(null, null));

        assertThat(response.items()).hasSize(1);
        assertThat(response.items().get(0).reference()).isEqualTo(deposit.getReference());
        assertThat(response.totalElements()).isEqualTo(1);
        assertThat(response.totalPages()).isEqualTo(1);
        assertThat(response.page()).isZero();
        assertThat(response.size()).isEqualTo(20);
    }

    @Test
    void aPartyCanLookUpATransactionAndTheReferenceIsTrimmedAndUppercased() {
        WalletTransaction deposit = deposit();
        when(transactionRepository.findByReference(deposit.getReference())).thenReturn(Optional.of(deposit));
        when(walletRepository.existsByIdAndUserId(walletId, userId)).thenReturn(true);

        TransactionDetailResponse response = service.findByReference(userId,
                "  " + deposit.getReference().toLowerCase() + " ");

        assertThat(response.reference()).isEqualTo(deposit.getReference());
        assertThat(response.amount()).isEqualTo("10.0000");
    }

    @Test
    void aStrangerCannotLookUpATransaction() {
        WalletTransaction deposit = deposit();
        when(transactionRepository.findByReference(deposit.getReference())).thenReturn(Optional.of(deposit));
        // walletRepository.existsByIdAndUserId is not stubbed, so it answers false: the caller owns neither wallet.

        assertThatThrownBy(() -> service.findByReference(userId, deposit.getReference()))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void anUnknownReferenceIsNotFound() {
        when(transactionRepository.findByReference("TXN-AAAAAAAAAA")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.findByReference(userId, "TXN-AAAAAAAAAA"))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
