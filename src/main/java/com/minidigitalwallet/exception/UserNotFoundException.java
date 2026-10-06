package com.minidigitalwallet.exception;

import org.springframework.http.HttpStatus;

import java.util.UUID;

public class UserNotFoundException extends AppException {
    public UserNotFoundException(String message) {
        super(message, HttpStatus.NOT_FOUND, "user-not-found");
    }

    public UserNotFoundException(UUID userId) {
        this("User not found: " + userId);
    }
}
