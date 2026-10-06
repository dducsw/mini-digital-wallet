package com.minidigitalwallet.exception;

import org.springframework.http.HttpStatus;

import java.util.UUID;

public class WalletNotFoundException extends AppException {
    public WalletNotFoundException(UUID id) {
        super("Wallet not found: " + id, HttpStatus.NOT_FOUND, "wallet-not-found");
    }

    public WalletNotFoundException(String message) {
        super(message, HttpStatus.NOT_FOUND, "wallet-not-found");
    }
}
