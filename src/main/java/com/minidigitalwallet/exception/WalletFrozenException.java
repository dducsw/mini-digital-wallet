package com.minidigitalwallet.exception;

import org.springframework.http.HttpStatus;

import java.util.UUID;

public class WalletFrozenException extends AppException {
    public WalletFrozenException(String message) {
        super(message, HttpStatus.FORBIDDEN, "wallet-frozen");
    }

    public WalletFrozenException(UUID walletId) {
        super("Wallet " + walletId + " is FROZEN", HttpStatus.FORBIDDEN, "wallet-frozen");
    }
}
