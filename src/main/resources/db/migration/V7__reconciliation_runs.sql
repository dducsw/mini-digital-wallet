-- V7: Phase 4 Reconciliation audit trail.
-- Each nightly/manual run persists one summary row (counts only, no balances).
-- Per-wallet mismatch details go to server logs, never to API responses.

CREATE TABLE IF NOT EXISTS reconciliation_runs (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    started_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    finished_at TIMESTAMPTZ,
    wallets_checked BIGINT NOT NULL DEFAULT 0,
    mismatched_count BIGINT NOT NULL DEFAULT 0,
    globally_balanced BOOLEAN NOT NULL DEFAULT TRUE,
    status VARCHAR(20) NOT NULL DEFAULT 'OK',
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT chk_recon_status CHECK (status IN ('OK', 'MISMATCH', 'ERROR'))
);

CREATE INDEX IF NOT EXISTS idx_recon_runs_created ON reconciliation_runs(created_at DESC);
