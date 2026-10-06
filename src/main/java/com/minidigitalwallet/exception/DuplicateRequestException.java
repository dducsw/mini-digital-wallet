package com.minidigitalwallet.exception;

import org.springframework.http.HttpStatus;

public class DuplicateRequestException extends AppException {
    public DuplicateRequestException(String message) {
        super(message, HttpStatus.CONFLICT, "duplicate-request");
    }
}
