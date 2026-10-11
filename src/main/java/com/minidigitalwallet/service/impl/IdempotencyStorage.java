package com.minidigitalwallet.service.impl;

import com.minidigitalwallet.entity.IdempotencyRecord;
import com.minidigitalwallet.entity.enums.IdempotencyStatus;
import com.minidigitalwallet.repository.IdempotencyRecordRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/**
 * Single-responsibility persistence boundary for idempotency leases.
 * Owns all SQL/lease mechanics so IdempotencyServiceImpl only orchestrates.
 */
@Component
@RequiredArgsConstructor
public class IdempotencyStorage {

    private final IdempotencyRecordRepository repository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public LeaseDecision acquireOrInspect(
        String actorId, String operation, String idempotencyKey, String requestHash
    ) {
        Instant now = Instant.now();
        Instant leaseExpiresAt = now.plusSeconds(30);

        int inserted = repository.insertIfNotExists(
            actorId, operation, idempotencyKey, requestHash, leaseExpiresAt, now
        );

        if (inserted > 0) {
            return LeaseDecision.acquired();
        }

        Optional<IdempotencyRecord> opt =
            repository.findByActorIdAndOperationAndIdempotencyKey(actorId, operation, idempotencyKey);

        if (opt.isPresent()) {
            IdempotencyRecord existing = opt.get();
            if (!Objects.equals(existing.getRequestHash(), requestHash)) {
                return LeaseDecision.payloadMismatch();
            }
            if (existing.getStatus() == IdempotencyStatus.COMPLETED) {
                return LeaseDecision.replay(existing.getResponseStatusCode(), existing.getResponseBody());
            }
            if (existing.getStatus() == IdempotencyStatus.IN_PROGRESS) {
                if (existing.getLeaseExpiresAt().isAfter(now)) {
                    return LeaseDecision.conflict();
                }
                int reclaimed = repository.tryReclaimLease(
                    actorId, operation, idempotencyKey, requestHash,
                    now.plusSeconds(30), now
                );
                return reclaimed > 0 ? LeaseDecision.acquired() : LeaseDecision.conflict();
            }
            if (existing.getStatus() == IdempotencyStatus.FAILED) {
                int reclaimed = repository.tryReclaimLease(
                    actorId, operation, idempotencyKey, requestHash,
                    now.plusSeconds(30), now
                );
                return reclaimed > 0 ? LeaseDecision.acquired() : LeaseDecision.conflict();
            }
        }

        return LeaseDecision.conflict();
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markCompleted(
        String actorId, String operation, String idempotencyKey, int statusCode, String responseBody
    ) {
        repository.findByActorIdAndOperationAndIdempotencyKey(actorId, operation, idempotencyKey)
            .ifPresent(record -> {
                record.setStatus(IdempotencyStatus.COMPLETED);
                record.setResponseStatusCode(statusCode);
                record.setResponseBody(responseBody);
                record.setUpdatedAt(Instant.now());
                repository.saveAndFlush(record);
            });
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markFailed(
        String actorId, String operation, String idempotencyKey, int statusCode, String responseBody
    ) {
        repository.findByActorIdAndOperationAndIdempotencyKey(actorId, operation, idempotencyKey)
            .ifPresent(record -> {
                record.setStatus(IdempotencyStatus.FAILED);
                record.setResponseStatusCode(statusCode);
                record.setResponseBody(responseBody);
                record.setUpdatedAt(Instant.now());
                repository.saveAndFlush(record);
            });
    }

    public record LeaseDecision(
        Type type,
        Integer statusCode,
        String responseBody
    ) {
        public enum Type {
            ACQUIRED,
            REPLAY,
            CONFLICT,
            PAYLOAD_MISMATCH
        }

        public static LeaseDecision acquired() {
            return new LeaseDecision(Type.ACQUIRED, null, null);
        }

        public static LeaseDecision replay(Integer statusCode, String responseBody) {
            return new LeaseDecision(Type.REPLAY, statusCode, responseBody);
        }

        public static LeaseDecision conflict() {
            return new LeaseDecision(Type.CONFLICT, null, null);
        }

        public static LeaseDecision payloadMismatch() {
            return new LeaseDecision(Type.PAYLOAD_MISMATCH, null, null);
        }
    }
}
