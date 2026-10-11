package com.minidigitalwallet.repository;

import com.minidigitalwallet.entity.IdempotencyRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface IdempotencyRecordRepository extends JpaRepository<IdempotencyRecord, UUID> {

    Optional<IdempotencyRecord> findByActorIdAndOperationAndIdempotencyKey(
        String actorId, String operation, String idempotencyKey
    );

    @Modifying
    @Query("""
        UPDATE IdempotencyRecord r
        SET r.status = com.minidigitalwallet.entity.enums.IdempotencyStatus.IN_PROGRESS,
            r.leaseExpiresAt = :newLeaseExpiresAt,
            r.requestHash = :requestHash,
            r.updatedAt = :now
        WHERE r.actorId = :actorId
          AND r.operation = :operation
          AND r.idempotencyKey = :idempotencyKey
          AND (
              r.status = com.minidigitalwallet.entity.enums.IdempotencyStatus.FAILED
              OR (r.status = com.minidigitalwallet.entity.enums.IdempotencyStatus.IN_PROGRESS AND r.leaseExpiresAt < :now)
          )
    """)
    int tryReclaimLease(
        @Param("actorId") String actorId,
        @Param("operation") String operation,
        @Param("idempotencyKey") String idempotencyKey,
        @Param("requestHash") String requestHash,
        @Param("newLeaseExpiresAt") Instant newLeaseExpiresAt,
        @Param("now") Instant now
    );

    @Modifying
    @Query(nativeQuery = true, value = """
        INSERT INTO idempotency_records (
            id, actor_id, operation, idempotency_key, request_hash, status, lease_expires_at, created_at, updated_at
        ) VALUES (
            gen_random_uuid(), :actorId, :operation, :idempotencyKey, :requestHash, 'IN_PROGRESS', :leaseExpiresAt, :now, :now
        )
        ON CONFLICT (actor_id, operation, idempotency_key) DO NOTHING
    """)
    int insertIfNotExists(
        @Param("actorId") String actorId,
        @Param("operation") String operation,
        @Param("idempotencyKey") String idempotencyKey,
        @Param("requestHash") String requestHash,
        @Param("leaseExpiresAt") Instant leaseExpiresAt,
        @Param("now") Instant now
    );
}
