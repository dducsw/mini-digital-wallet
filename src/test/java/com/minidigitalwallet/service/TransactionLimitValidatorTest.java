package com.minidigitalwallet.service;

import com.minidigitalwallet.entity.Wallet;
import com.minidigitalwallet.entity.enums.TransactionType;
import com.minidigitalwallet.entity.enums.WalletStatus;
import com.minidigitalwallet.entity.enums.WalletType;
import com.minidigitalwallet.exception.AppException;
import com.minidigitalwallet.repository.LedgerEntryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TransactionLimitValidatorTest {

    @Mock
    private LedgerEntryRepository ledgerEntryRepository;

    private TransactionLimitValidator validator;

    private final UUID walletId = UUID.randomUUID();
    private Wallet userWallet;

    @BeforeEach
    void setUp() {
        validator = new TransactionLimitValidator(ledgerEntryRepository);
        ReflectionTestUtils.setField(validator, "maxAmountPerTx", 1_000_000L); // $10,000 max
        ReflectionTestUtils.setField(validator, "dailyMaxAmount", 5_000_000L); // $50,000 daily

        userWallet = Wallet.builder()
            .id(walletId)
            .userId("USER_A")
            .walletType(WalletType.USER)
            .currency("USD")
            .availableBalance(10_000_000L)
            .ledgerBalance(10_000_000L)
            .status(WalletStatus.ACTIVE)
            .build();
    }

    @Test
    @DisplayName("Amount within the single transaction limit passes")
    void validateAmount_WithinLimit_Passes() {
        assertThatCode(() -> validator.validateAmount(200_000L))
            .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Amount exceeding the single transaction limit throws 403 limit-exceeded-single")
    void validateAmount_ExceedsLimit_ThrowsForbidden() {
        assertThatThrownBy(() -> validator.validateAmount(1_500_000L))
            .isInstanceOf(AppException.class)
            .satisfies(ex -> {
                AppException appEx = (AppException) ex;
                assertThat(appEx.getStatus()).isEqualTo(HttpStatus.FORBIDDEN);
                assertThat(appEx.getErrorCode()).isEqualTo("limit-exceeded-single");
            });
    }

    @Test
    @DisplayName("Debit within the daily cumulative limit passes")
    void check_WithinDailyLimit_Passes() {
        when(ledgerEntryRepository.sumDebitsSince(eq(walletId), any(Instant.class)))
            .thenReturn(500_000L);

        assertThatCode(() -> validator.check(userWallet, TransactionType.P2P_TRANSFER, 200_000L))
            .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Debit exceeding the daily cumulative limit throws 403 limit-exceeded-daily")
    void check_ExceedsDailyLimit_ThrowsForbidden() {
        when(ledgerEntryRepository.sumDebitsSince(eq(walletId), any(Instant.class)))
            .thenReturn(4_800_000L);

        assertThatThrownBy(() -> validator.check(userWallet, TransactionType.P2P_TRANSFER, 300_000L))
            .isInstanceOf(AppException.class)
            .satisfies(ex -> {
                AppException appEx = (AppException) ex;
                assertThat(appEx.getStatus()).isEqualTo(HttpStatus.FORBIDDEN);
                assertThat(appEx.getErrorCode()).isEqualTo("limit-exceeded-daily");
            });
    }

    @Test
    @DisplayName("Reversals are refunds, not spending: never daily-capped")
    void check_ReversalIsNotDailyCapped() {
        assertThatCode(() -> validator.check(userWallet, TransactionType.REVERSAL, 9_000_000L))
            .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("SYSTEM/settlement accounts are internal contra accounts: never daily-capped")
    void check_SystemWalletIsNotDailyCapped() {
        Wallet systemWallet = Wallet.builder()
            .id(UUID.randomUUID())
            .userId("SYS_EXTERNAL_SETTLEMENT")
            .walletType(WalletType.SYSTEM)
            .currency("USD")
            .availableBalance(0L)
            .ledgerBalance(0L)
            .status(WalletStatus.ACTIVE)
            .build();

        assertThatCode(() -> validator.check(systemWallet, TransactionType.TOPUP, 9_000_000L))
            .doesNotThrowAnyException();
    }
}
