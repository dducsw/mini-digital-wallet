package com.minidigitalwallet.exception;

import org.springframework.http.HttpStatus;

public class InsufficientFundsException extends AppException {
    public InsufficientFundsException(String message) {
        super(message, HttpStatus.BAD_REQUEST, "insufficient-balance");
    }
}
