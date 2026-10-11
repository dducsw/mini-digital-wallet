package com.minidigitalwallet.service;

import org.springframework.http.ResponseEntity;

import java.util.function.Supplier;

public interface IdempotencyService {

    <T> ResponseEntity<T> execute(
        String actorId,
        String operation,
        String idempotencyKey,
        Object requestPayload,
        Class<T> responseType,
        Supplier<ResponseEntity<T>> action
    );
}
