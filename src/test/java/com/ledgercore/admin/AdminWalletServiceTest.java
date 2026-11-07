package com.ledgercore.admin;

import com.ledgercore.admin.dto.AdminWalletResponse;
import com.ledgercore.audit.AuditAction;
import com.ledgercore.audit.AuditService;
import com.ledgercore.audit.AuditTargetType;
import com.ledgercore.common.ResourceNotFoundException;
import com.ledgercore.common.StateConflictException;
import com.ledgercore.wallet.SupportedCurrency;
import com.ledgercore.wallet.Wallet;
import com.ledgercore.wallet.WalletRepository;
import com.ledgercore.wallet.WalletStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminWalletServiceTest {

    @Mock private WalletRepository walletRepository;
    @Mock private AuditService auditService;

    @InjectMocks private AdminWalletService service;

    private final UUID actor = UUID.randomUUID();
    private final UUID walletId = UUID.randomUUID();

    private Wallet wallet() {
        Wallet wallet = new Wallet(UUID.randomUUID(), SupportedCurrency.USD);
        ReflectionTestUtils.setField(wallet, "id", walletId);
        return wallet;
    }

    private void verifyNothingChanged() {
        verify(auditService, never()).record(any(), any(), any(), any(), any(), any());
        verify(walletRepository, never()).saveAndFlush(any(Wallet.class));
    }

    @Test
    void freezeFreezesTheWalletAndRecordsATrimmedReason() {
        Wallet wallet = wallet();
        when(walletRepository.findByIdForUpdate(walletId)).thenReturn(Optional.of(wallet));

        AdminWalletResponse response = service.freeze(actor, walletId, "  fraud review ");

        assertThat(wallet.getStatus()).isEqualTo(WalletStatus.FROZEN);
        assertThat(response.status()).isEqualTo(WalletStatus.FROZEN);
        verify(walletRepository).saveAndFlush(wallet);
        verify(auditService).record(eq(actor), eq(AuditAction.WALLET_FROZEN), eq(AuditTargetType.WALLET),
                eq(walletId), eq("fraud review"), eq("was ACTIVE"));
    }

    @Test
    void freezingAnAlreadyFrozenWalletIsAConflictAndChangesNothing() {
        Wallet wallet = wallet();
        wallet.freeze();
        when(walletRepository.findByIdForUpdate(walletId)).thenReturn(Optional.of(wallet));

        assertThatThrownBy(() -> service.freeze(actor, walletId, "again"))
                .isInstanceOf(StateConflictException.class);

        verifyNothingChanged();
    }

    @Test
    void freezingAnUnknownWalletIsNotFound() {
        when(walletRepository.findByIdForUpdate(walletId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.freeze(actor, walletId, "who"))
                .isInstanceOf(ResourceNotFoundException.class);

        verifyNothingChanged();
    }

    @Test
    void unfreezeActivatesTheWalletAndRecordsIt() {
        Wallet wallet = wallet();
        wallet.freeze();
        when(walletRepository.findByIdForUpdate(walletId)).thenReturn(Optional.of(wallet));

        AdminWalletResponse response = service.unfreeze(actor, walletId, "review finished");

        assertThat(wallet.getStatus()).isEqualTo(WalletStatus.ACTIVE);
        assertThat(response.status()).isEqualTo(WalletStatus.ACTIVE);
        verify(auditService).record(eq(actor), eq(AuditAction.WALLET_UNFROZEN), eq(AuditTargetType.WALLET),
                eq(walletId), eq("review finished"), eq("was FROZEN"));
    }

    @Test
    void unfreezingAnActiveWalletIsAConflictAndChangesNothing() {
        when(walletRepository.findByIdForUpdate(walletId)).thenReturn(Optional.of(wallet()));

        assertThatThrownBy(() -> service.unfreeze(actor, walletId, "not needed"))
                .isInstanceOf(StateConflictException.class);

        verifyNothingChanged();
    }

    @Test
    void gettingAnUnknownWalletIsNotFound() {
        when(walletRepository.findById(walletId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.get(walletId)).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void theListIsNewestFirstWithAnIdTieBreakerAndUsesTheRequestedPage() {
        when(walletRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<Wallet>(List.of()));

        service.list(WalletFilter.of(null, null, null, 1, 5));

        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(walletRepository).findAll(any(Specification.class), pageable.capture());
        assertThat(pageable.getValue().getPageNumber()).isEqualTo(1);
        assertThat(pageable.getValue().getPageSize()).isEqualTo(5);
        Sort sort = pageable.getValue().getSort();
        assertThat(sort.stream().map(Sort.Order::getProperty)).containsExactly("createdAt", "id");
        assertThat(sort.getOrderFor("createdAt").isDescending()).isTrue();
        assertThat(sort.getOrderFor("id").isDescending()).isTrue();
    }
}
