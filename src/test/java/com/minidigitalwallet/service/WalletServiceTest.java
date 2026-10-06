package com.minidigitalwallet.service;

import com.minidigitalwallet.dto.request.CreateWalletRequest;
import com.minidigitalwallet.dto.request.UpdateWalletStatusRequest;
import com.minidigitalwallet.dto.response.WalletResponse;
import com.minidigitalwallet.entity.Wallet;
import com.minidigitalwallet.entity.enums.WalletStatus;
import com.minidigitalwallet.entity.enums.WalletType;
import com.minidigitalwallet.exception.AppException;
import com.minidigitalwallet.exception.DuplicateRequestException;
import com.minidigitalwallet.exception.WalletNotFoundException;
import com.minidigitalwallet.repository.WalletRepository;
import com.minidigitalwallet.service.impl.WalletServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class WalletServiceTest {

    @Mock
    private WalletRepository walletRepository;

    @InjectMocks
    private WalletServiceImpl walletService;

    private UUID walletId;
    private Wallet mockWallet;

    @BeforeEach
    void setUp() {
        walletId = UUID.randomUUID();
        mockWallet = Wallet.builder()
            .id(walletId)
            .userId("USER_123")
            .walletType(WalletType.USER)
            .currency("USD")
            .availableBalance(10000L)
            .ledgerBalance(10000L)
            .status(WalletStatus.ACTIVE)
            .version(1L)
            .createdAt(Instant.now())
            .updatedAt(Instant.now())
            .build();
    }

    @Test
    @DisplayName("createWallet should succeed when wallet does not already exist")
    void createWallet_Success() {
        CreateWalletRequest request = new CreateWalletRequest("USER_123", "USD", WalletType.USER);

        when(walletRepository.existsByUserIdAndCurrencyAndWalletType("USER_123", "USD", WalletType.USER))
            .thenReturn(false);
        when(walletRepository.save(any(Wallet.class))).thenAnswer(invocation -> {
            Wallet w = invocation.getArgument(0);
            w.setId(walletId);
            w.setCreatedAt(Instant.now());
            w.setUpdatedAt(Instant.now());
            return w;
        });

        WalletResponse response = walletService.createWallet(request);

        assertThat(response).isNotNull();
        assertThat(response.id()).isEqualTo(walletId);
        assertThat(response.ownerId()).isEqualTo("USER_123");
        assertThat(response.availableBalance()).isEqualTo(0L);
        assertThat(response.ledgerBalance()).isEqualTo(0L);
        assertThat(response.status()).isEqualTo(WalletStatus.ACTIVE);
        verify(walletRepository, times(1)).save(any(Wallet.class));
    }

    @Test
    @DisplayName("createWallet should throw DuplicateRequestException when wallet already exists")
    void createWallet_Duplicate_ThrowsException() {
        CreateWalletRequest request = new CreateWalletRequest("USER_123", "USD", WalletType.USER);

        when(walletRepository.existsByUserIdAndCurrencyAndWalletType("USER_123", "USD", WalletType.USER))
            .thenReturn(true);

        assertThatThrownBy(() -> walletService.createWallet(request))
            .isInstanceOf(DuplicateRequestException.class)
            .hasMessageContaining("Wallet already exists");

        verify(walletRepository, never()).save(any(Wallet.class));
    }

    @Test
    @DisplayName("getWallet should return wallet response when found")
    void getWallet_Success() {
        when(walletRepository.findById(walletId)).thenReturn(Optional.of(mockWallet));

        WalletResponse response = walletService.getWallet(walletId);

        assertThat(response.id()).isEqualTo(walletId);
        assertThat(response.ownerId()).isEqualTo("USER_123");
        assertThat(response.availableBalance()).isEqualTo(10000L);
    }

    @Test
    @DisplayName("getWallet should throw WalletNotFoundException when not found")
    void getWallet_NotFound_ThrowsException() {
        UUID nonExistentId = UUID.randomUUID();
        when(walletRepository.findById(nonExistentId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> walletService.getWallet(nonExistentId))
            .isInstanceOf(WalletNotFoundException.class);
    }

    @Test
    @DisplayName("getWalletBalance should return balance correctly")
    void getWalletBalance_Success() {
        when(walletRepository.findById(walletId)).thenReturn(Optional.of(mockWallet));

        WalletResponse response = walletService.getWalletBalance(walletId);

        assertThat(response.availableBalance()).isEqualTo(10000L);
        assertThat(response.ledgerBalance()).isEqualTo(10000L);
    }

    @Test
    @DisplayName("getWalletsByUserId should return all wallets of user")
    void getWalletsByUserId_Success() {
        when(walletRepository.findByUserId("USER_123")).thenReturn(List.of(mockWallet));

        List<WalletResponse> list = walletService.getWalletsByUserId("USER_123");

        assertThat(list).hasSize(1);
        assertThat(list.get(0).ownerId()).isEqualTo("USER_123");
    }

    @Test
    @DisplayName("updateStatus should freeze active wallet")
    void updateStatus_Freeze_Success() {
        when(walletRepository.findById(walletId)).thenReturn(Optional.of(mockWallet));
        when(walletRepository.save(any(Wallet.class))).thenAnswer(i -> i.getArgument(0));

        WalletResponse response = walletService.updateStatus(walletId, new UpdateWalletStatusRequest(WalletStatus.FROZEN));

        assertThat(response.status()).isEqualTo(WalletStatus.FROZEN);
    }

    @Test
    @DisplayName("updateStatus should reject closing wallet when balance is non-zero")
    void updateStatus_CloseWithBalance_ThrowsException() {
        when(walletRepository.findById(walletId)).thenReturn(Optional.of(mockWallet));

        assertThatThrownBy(() -> walletService.updateStatus(walletId, new UpdateWalletStatusRequest(WalletStatus.CLOSED)))
            .isInstanceOf(AppException.class)
            .hasMessageContaining("Cannot close wallet with remaining balance");
    }
}
