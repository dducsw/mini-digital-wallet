package com.minidigitalwallet.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.minidigitalwallet.exception.AppException;
import com.minidigitalwallet.service.IdempotencyService;
import com.minidigitalwallet.service.impl.IdempotencyStorage.LeaseDecision;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.function.Supplier;

@Service
@RequiredArgsConstructor
@Slf4j
public class IdempotencyServiceImpl implements IdempotencyService {

    private final ObjectMapper objectMapper;
    private final IdempotencyStorage storage;

    @Override
    public <T> ResponseEntity<T> execute(
        String actorId,
        String operation,
        String idempotencyKey,
        Object requestPayload,
        Class<T> responseType,
        Supplier<ResponseEntity<T>> action
    ) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            return action.get();
        }

        String requestHash = computeHash(requestPayload);
        LeaseDecision decision = storage.acquireOrInspect(actorId, operation, idempotencyKey, requestHash);

        switch (decision.type()) {
            case PAYLOAD_MISMATCH -> throw new AppException(
                "Payload hash does not match previous request with this idempotency key",
                HttpStatus.UNPROCESSABLE_ENTITY,
                "idempotency-payload-mismatch"
            );
            case CONFLICT -> throw new AppException(
                "Transaction is currently processing",
                HttpStatus.CONFLICT,
                "idempotency-in-progress"
            );
            case REPLAY -> {
                try {
                    T cachedBody = objectMapper.readValue(decision.responseBody(), responseType);
                    return ResponseEntity.status(decision.statusCode()).body(cachedBody);
                } catch (Exception e) {
                    log.error("Failed to deserialize cached response for key {}: {}", idempotencyKey, e.getMessage());
                    throw new AppException(
                        "Failed to read cached idempotency response",
                        HttpStatus.INTERNAL_SERVER_ERROR,
                        "idempotency-cache-error"
                    );
                }
            }
            case ACQUIRED -> {
                try {
                    ResponseEntity<T> response = action.get();
                    String bodyJson = objectMapper.writeValueAsString(response.getBody());
                    storage.markCompleted(
                        actorId, operation, idempotencyKey, response.getStatusCode().value(), bodyJson
                    );
                    return response;
                } catch (AppException e) {
                    // FAILED không bao giờ replay (luôn reclaim ở lần sau) nên body để NULL:
                    // tránh nhánh REPLAY deserialize nhầm plain-text thành JSON.
                    storage.markFailed(actorId, operation, idempotencyKey, e.getStatus().value(), null);
                    throw e;
                } catch (Exception e) {
                    storage.markFailed(actorId, operation, idempotencyKey, 500, null);
                    if (e instanceof RuntimeException re) {
                        throw re;
                    }
                    throw new RuntimeException(e);
                }
            }
        }

        throw new AppException("Unexpected idempotency state", HttpStatus.INTERNAL_SERVER_ERROR, "idempotency-unknown-state");
    }

    private String computeHash(Object payload) {
        try {
            String json = payload instanceof String str ? str : objectMapper.writeValueAsString(payload);
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashBytes = digest.digest(json.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hashBytes);
        } catch (Exception e) {
            throw new RuntimeException("Failed to compute payload hash", e);
        }
    }

}
