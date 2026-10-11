package com.minidigitalwallet.domain.state;

import com.minidigitalwallet.domain.model.Money;
import com.minidigitalwallet.entity.Wallet;
import com.minidigitalwallet.exception.AppException;
import org.springframework.http.HttpStatus;

public class ClosedWalletState implements WalletState {

    public static final ClosedWalletState INSTANCE = new ClosedWalletState();

    private ClosedWalletState() {
    }

    @Override
    public void debit(Wallet wallet, Money money) {
        throw new AppException("Wallet is CLOSED: cannot debit", HttpStatus.BAD_REQUEST, "wallet-closed");
    }

    @Override
    public void credit(Wallet wallet, Money money) {
        throw new AppException("Wallet is CLOSED: cannot credit", HttpStatus.BAD_REQUEST, "wallet-closed");
    }

    @Override
    public void freeze(Wallet wallet) {
        throw new AppException("Wallet is CLOSED: cannot freeze", HttpStatus.BAD_REQUEST, "wallet-closed");
    }

    @Override
    public void unfreeze(Wallet wallet) {
        throw new AppException("Wallet is CLOSED: cannot unfreeze", HttpStatus.BAD_REQUEST, "wallet-closed");
    }

    @Override
    public void close(Wallet wallet) {
        // Already closed, no-op
    }
}
