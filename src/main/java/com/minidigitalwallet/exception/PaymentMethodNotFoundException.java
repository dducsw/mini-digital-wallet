package com.minidigitalwallet.exception;

import org.springframework.http.HttpStatus;

import java.util.UUID;

public class PaymentMethodNotFoundException extends AppException {
    public PaymentMethodNotFoundException(UUID id) {
        super("Payment method not found: " + id, HttpStatus.NOT_FOUND, "payment-method-not-found");
    }

    public PaymentMethodNotFoundException(String message) {
        super(message, HttpStatus.NOT_FOUND, "payment-method-not-found");
    }
}
