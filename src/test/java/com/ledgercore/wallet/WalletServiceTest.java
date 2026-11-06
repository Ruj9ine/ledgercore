package com.ledgercore.wallet;

import com.ledgercore.common.ResourceNotFoundException;
import com.ledgercore.common.WalletAlreadyExistsException;
import com.ledgercore.wallet.dto.WalletResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WalletServiceTest {

    @Mock private WalletRepository walletRepository;

    @InjectMocks private WalletService walletService;

    private final UUID userId = UUID.randomUUID();

    @Test
    void createStartsWithZeroBalanceForTheCaller() {
        when(walletRepository.existsByUserIdAndCurrency(userId, SupportedCurrency.UGX)).thenReturn(false);
        when(walletRepository.saveAndFlush(any(Wallet.class))).thenAnswer(invocation -> invocation.getArgument(0));

        WalletResponse response = walletService.create(userId, SupportedCurrency.UGX);

        ArgumentCaptor<Wallet> saved = ArgumentCaptor.forClass(Wallet.class);
        verify(walletRepository).saveAndFlush(saved.capture());
        assertThat(saved.getValue().getUserId()).isEqualTo(userId);
        assertThat(saved.getValue().getCurrency()).isEqualTo(SupportedCurrency.UGX);
        assertThat(saved.getValue().getBalance()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(response.currency()).isEqualTo(SupportedCurrency.UGX);
    }

    @Test
    void createRejectsSecondWalletInTheSameCurrencyWithoutSaving() {
        when(walletRepository.existsByUserIdAndCurrency(userId, SupportedCurrency.UGX)).thenReturn(true);

        assertThatThrownBy(() -> walletService.create(userId, SupportedCurrency.UGX))
                .isInstanceOf(WalletAlreadyExistsException.class);

        verify(walletRepository, never()).saveAndFlush(any(Wallet.class));
    }

    @Test
    void createTurnsAUniqueConstraintRaceIntoAConflict() {
        when(walletRepository.existsByUserIdAndCurrency(userId, SupportedCurrency.USD)).thenReturn(false);
        when(walletRepository.saveAndFlush(any(Wallet.class))).thenThrow(new DataIntegrityViolationException("duplicate"));

        assertThatThrownBy(() -> walletService.create(userId, SupportedCurrency.USD))
                .isInstanceOf(WalletAlreadyExistsException.class);
    }

    @Test
    void getOwnedReportsAnotherUsersWalletAsNotFound() {
        UUID walletId = UUID.randomUUID();
        when(walletRepository.findByIdAndUserId(walletId, userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> walletService.getOwned(walletId, userId))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
