package com.minidigitalwallet.service;

import com.minidigitalwallet.entity.LedgerTransaction;
import com.minidigitalwallet.entity.Wallet;
import com.minidigitalwallet.entity.enums.EntryType;
import com.minidigitalwallet.entity.enums.TransactionType;
import com.minidigitalwallet.entity.enums.WalletStatus;
import com.minidigitalwallet.entity.enums.WalletType;
import com.minidigitalwallet.exception.AppException;
import com.minidigitalwallet.exception.DuplicateRequestException;
import com.minidigitalwallet.exception.InsufficientFundsException;
import com.minidigitalwallet.exception.WalletFrozenException;
import com.minidigitalwallet.repository.LedgerTransactionRepository;
import com.minidigitalwallet.repository.WalletRepository;
import com.minidigitalwallet.service.impl.LedgerServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LedgerServiceTest {

    @Mock
    private WalletRepository walletRepository;

    @Mock
    private LedgerTransactionRepository transactionRepository;

    @InjectMocks
    private LedgerServiceImpl ledgerService;

    private Wallet walletA;
    private Wallet walletB;

    @BeforeEach
    void setUp() {
        walletA = Wallet.builder()
            .id(UUID.fromString("11111111-1111-1111-1111-111111111111"))
            .userId("USER_A")
            .walletType(WalletType.USER)
            .currency("USD")
            .availableBalance(10000L)
            .ledgerBalance(10000L)
            .status(WalletStatus.ACTIVE)
            .build();

        walletB = Wallet.builder()
            .id(UUID.fromString("22222222-2222-2222-2222-222222222222"))
            .userId("USER_B")
            .walletType(WalletType.USER)
            .currency("USD")
            .availableBalance(5000L)
            .ledgerBalance(5000L)
            .status(WalletStatus.ACTIVE)
            .build();
    }

    @Test
    @DisplayName("executeTransfer: Successful double-entry transfer between two accounts")
    void executeTransfer_Success() {
        when(transactionRepository.existsByReferenceId("REF_001")).thenReturn(false);
        when(walletRepository.findByIdForUpdate(walletA.getId())).thenReturn(Optional.of(walletA));
        when(walletRepository.findByIdForUpdate(walletB.getId())).thenReturn(Optional.of(walletB));
        when(transactionRepository.save(any(LedgerTransaction.class))).thenAnswer(i -> i.getArgument(0));

        LedgerTransaction tx = ledgerService.executeTransfer(
            "REF_001",
            walletA.getId(),
            walletB.getId(),
            3000L,
            TransactionType.P2P_TRANSFER,
            "Transfer test"
        );

        assertThat(tx).isNotNull();
        assertThat(tx.getAmount()).isEqualTo(3000L);
        assertThat(walletA.getAvailableBalance()).isEqualTo(7000L);
        assertThat(walletB.getAvailableBalance()).isEqualTo(8000L);

        assertThat(tx.getEntries()).hasSize(2);
        assertThat(tx.getEntries().get(0).getEntryType()).isEqualTo(EntryType.DEBIT);
        assertThat(tx.getEntries().get(0).getAmount()).isEqualTo(3000L);
        assertThat(tx.getEntries().get(1).getEntryType()).isEqualTo(EntryType.CREDIT);
        assertThat(tx.getEntries().get(1).getAmount()).isEqualTo(3000L);

        verify(walletRepository).save(walletA);
        verify(walletRepository).save(walletB);
        verify(transactionRepository).save(any(LedgerTransaction.class));
    }

    @Test
    @DisplayName("executeTransfer: Insufficient funds throws InsufficientFundsException")
    void executeTransfer_InsufficientFunds() {
        when(transactionRepository.existsByReferenceId("REF_002")).thenReturn(false);
        when(walletRepository.findByIdForUpdate(walletA.getId())).thenReturn(Optional.of(walletA));
        when(walletRepository.findByIdForUpdate(walletB.getId())).thenReturn(Optional.of(walletB));

        assertThatThrownBy(() -> ledgerService.executeTransfer(
            "REF_002",
            walletA.getId(),
            walletB.getId(),
            50000L,
            TransactionType.P2P_TRANSFER,
            "Overdraft test"
        )).isInstanceOf(InsufficientFundsException.class);

        assertThat(walletA.getAvailableBalance()).isEqualTo(10000L);
        assertThat(walletB.getAvailableBalance()).isEqualTo(5000L);
        verify(transactionRepository, never()).save(any());
    }

    @Test
    @DisplayName("executeTransfer: Self transfer is disallowed")
    void executeTransfer_SelfTransfer_ThrowsException() {
        assertThatThrownBy(() -> ledgerService.executeTransfer(
            "REF_SELF",
            walletA.getId(),
            walletA.getId(),
            1000L,
            TransactionType.P2P_TRANSFER,
            "Self transfer"
        )).isInstanceOf(AppException.class);
    }

    @Test
    @DisplayName("executeTransfer: Source frozen throws WalletFrozenException")
    void executeTransfer_FrozenSource_ThrowsException() {
        walletA.setStatus(WalletStatus.FROZEN);
        when(transactionRepository.existsByReferenceId("REF_FROZEN")).thenReturn(false);
        when(walletRepository.findByIdForUpdate(walletA.getId())).thenReturn(Optional.of(walletA));
        when(walletRepository.findByIdForUpdate(walletB.getId())).thenReturn(Optional.of(walletB));

        assertThatThrownBy(() -> ledgerService.executeTransfer(
            "REF_FROZEN",
            walletA.getId(),
            walletB.getId(),
            1000L,
            TransactionType.P2P_TRANSFER,
            "Frozen source test"
        )).isInstanceOf(WalletFrozenException.class);
    }

    @Test
    @DisplayName("executeTransfer: Duplicate referenceId throws DuplicateRequestException")
    void executeTransfer_DuplicateReference_ThrowsException() {
        when(transactionRepository.existsByReferenceId("REF_DUP")).thenReturn(true);

        assertThatThrownBy(() -> ledgerService.executeTransfer(
            "REF_DUP",
            walletA.getId(),
            walletB.getId(),
            1000L,
            TransactionType.P2P_TRANSFER,
            "Duplicate test"
        )).isInstanceOf(DuplicateRequestException.class);
    }
}
