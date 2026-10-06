package com.minidigitalwallet.exception;

import org.springframework.http.HttpStatus;

public class InvalidCredentialsException extends AppException {
    public InvalidCredentialsException(String message) {
        super(message, HttpStatus.UNAUTHORIZED, "invalid-credentials");
    }

    public InvalidCredentialsException() {
        this("Invalid username or password");
    }
}
