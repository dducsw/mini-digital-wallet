package com.minidigitalwallet.domain.state;

import com.minidigitalwallet.domain.model.Money;
import com.minidigitalwallet.entity.Wallet;
import com.minidigitalwallet.entity.enums.WalletStatus;
import com.minidigitalwallet.exception.AppException;
import com.minidigitalwallet.exception.WalletFrozenException;
import org.springframework.http.HttpStatus;

public class FrozenWalletState implements WalletState {

    public static final FrozenWalletState INSTANCE = new FrozenWalletState();

    private FrozenWalletState() {
    }

    @Override
    public void debit(Wallet wallet, Money money) {
        throw new WalletFrozenException(wallet.getId());
    }

    @Override
    public void credit(Wallet wallet, Money money) {
        throw new WalletFrozenException(wallet.getId());
    }

    @Override
    public void freeze(Wallet wallet) {
        // Already frozen, no-op
    }

    @Override
    public void unfreeze(Wallet wallet) {
        wallet.setStatus(WalletStatus.ACTIVE);
    }

    @Override
    public void close(Wallet wallet) {
        if (wallet.getAvailableBalance() != null && wallet.getAvailableBalance() > 0) {
            throw new AppException(
                "Cannot close wallet with remaining balance: " + wallet.getAvailableBalance() + " cents",
                HttpStatus.BAD_REQUEST,
                "wallet-balance-non-zero"
            );
        }
        wallet.setStatus(WalletStatus.CLOSED);
    }
}
