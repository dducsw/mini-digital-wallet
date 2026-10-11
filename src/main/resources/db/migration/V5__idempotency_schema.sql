-- V5: Scoped PostgreSQL Idempotency & Lease Recovery (Pure Modular Monolith)

CREATE TABLE IF NOT EXISTS idempotency_records (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    actor_id VARCHAR(64) NOT NULL,
    operation VARCHAR(32) NOT NULL,
    idempotency_key VARCHAR(128) NOT NULL,
    request_hash VARCHAR(64) NOT NULL,
    status VARCHAR(20) NOT NULL,
    response_status_code INT,
    response_body TEXT,
    lease_expires_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT uq_idempotency_scope UNIQUE (actor_id, operation, idempotency_key),
    CONSTRAINT chk_idempotency_status CHECK (status IN ('IN_PROGRESS', 'COMPLETED', 'FAILED'))
);

CREATE INDEX IF NOT EXISTS idx_idempotency_scope ON idempotency_records(actor_id, operation, idempotency_key);
CREATE INDEX IF NOT EXISTS idx_idempotency_lease ON idempotency_records(lease_expires_at);
