package com.minidigitalwallet.domain.state;

import com.minidigitalwallet.domain.model.Money;
import com.minidigitalwallet.entity.Wallet;
import com.minidigitalwallet.entity.enums.WalletStatus;
import com.minidigitalwallet.entity.enums.WalletType;
import com.minidigitalwallet.exception.AppException;
import com.minidigitalwallet.exception.InsufficientFundsException;
import org.springframework.http.HttpStatus;

public class ActiveWalletState implements WalletState {

    public static final ActiveWalletState INSTANCE = new ActiveWalletState();

    private ActiveWalletState() {
    }

    @Override
    public void debit(Wallet wallet, Money money) {
        requirePositive(money.amountInCents(), "Debit");
        if (wallet.getWalletType() == WalletType.USER && wallet.getAvailableBalance() < money.amountInCents()) {
            throw new InsufficientFundsException(
                "Insufficient balance: " + wallet.getAvailableBalance() + " cents available, required: " + money.amountInCents() + " cents"
            );
        }
        wallet.setAvailableBalance(wallet.getAvailableBalance() - money.amountInCents());
        wallet.setLedgerBalance(wallet.getLedgerBalance() - money.amountInCents());
    }

    @Override
    public void credit(Wallet wallet, Money money) {
        requirePositive(money.amountInCents(), "Credit");
        wallet.setAvailableBalance(wallet.getAvailableBalance() + money.amountInCents());
        wallet.setLedgerBalance(wallet.getLedgerBalance() + money.amountInCents());
    }

    @Override
    public void freeze(Wallet wallet) {
        wallet.setStatus(WalletStatus.FROZEN);
    }

    @Override
    public void unfreeze(Wallet wallet) {
        // Already active, no-op
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

    private static void requirePositive(Long amountInCents, String operation) {
        if (amountInCents == null || amountInCents <= 0) {
            throw new AppException(operation + " amount must be positive", HttpStatus.BAD_REQUEST, "invalid-amount");
        }
    }
}
