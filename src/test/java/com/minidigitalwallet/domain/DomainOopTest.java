package com.minidigitalwallet.domain;

import com.minidigitalwallet.domain.model.Money;
import com.minidigitalwallet.domain.state.ActiveWalletState;
import com.minidigitalwallet.domain.state.FrozenWalletState;
import com.minidigitalwallet.entity.Wallet;
import com.minidigitalwallet.entity.enums.WalletStatus;
import com.minidigitalwallet.entity.enums.WalletType;
import com.minidigitalwallet.exception.InsufficientFundsException;
import com.minidigitalwallet.exception.WalletFrozenException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DomainOopTest {

    @Test
    @DisplayName("Money: Value object immutability and arithmetic validation")
    void moneyValueObject() {
        Money m1 = Money.of(1000L, "USD");
        Money m2 = Money.of(500L, "USD");

        Money sum = m1.plus(m2);
        assertThat(sum.amountInCents()).isEqualTo(1500L);
        assertThat(sum.currency()).isEqualTo("USD");

        Money diff = m1.minus(m2);
        assertThat(diff.amountInCents()).isEqualTo(500L);

        // Disallow negative
        assertThatThrownBy(() -> Money.of(-10L, "USD"))
            .isInstanceOf(RuntimeException.class);

        // Disallow currency mismatch
        Money eur = Money.of(500L, "EUR");
        assertThatThrownBy(() -> m1.plus(eur))
            .isInstanceOf(RuntimeException.class);
    }

    @Test
    @DisplayName("WalletState: State Pattern correctly permits or rejects operations based on state")
    void walletStateTransitions() {
        Wallet wallet = Wallet.builder()
            .id(UUID.randomUUID())
            .userId("USR_123")
            .walletType(WalletType.USER)
            .currency("USD")
            .availableBalance(1000L)
            .ledgerBalance(1000L)
            .status(WalletStatus.ACTIVE)
            .build();

        // 1. ACTIVE State
        assertThat(wallet.getState()).isInstanceOf(ActiveWalletState.class);
        wallet.debit(Money.of(300L, "USD"));
        assertThat(wallet.getAvailableBalance()).isEqualTo(700L);

        // Insufficient funds
        assertThatThrownBy(() -> wallet.debit(Money.of(1000L, "USD")))
            .isInstanceOf(InsufficientFundsException.class);

        // 2. Freeze Wallet -> FROZEN State
        wallet.freeze();
        assertThat(wallet.getStatus()).isEqualTo(WalletStatus.FROZEN);
        assertThat(wallet.getState()).isInstanceOf(FrozenWalletState.class);

        // Debit/Credit on frozen wallet should fail
        assertThatThrownBy(() -> wallet.debit(Money.of(100L, "USD")))
            .isInstanceOf(WalletFrozenException.class);
        assertThatThrownBy(() -> wallet.credit(Money.of(100L, "USD")))
            .isInstanceOf(WalletFrozenException.class);

        // 3. Unfreeze -> back to ACTIVE State
        wallet.unfreeze();
        assertThat(wallet.getStatus()).isEqualTo(WalletStatus.ACTIVE);
        wallet.credit(Money.of(500L, "USD"));
        assertThat(wallet.getAvailableBalance()).isEqualTo(1200L);
    }
}
