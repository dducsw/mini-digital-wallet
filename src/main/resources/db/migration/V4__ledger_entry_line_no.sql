-- V4: Enforce double-entry line ordering: UNIQUE(transaction_id, line_no)
-- Phase 2 fix: PLAN requires UNIQUE(transaction_id, line_no).

ALTER TABLE ledger_entries ADD COLUMN IF NOT EXISTS line_no INT;

-- Backfill existing rows (2 lines per tx ordered by created_at) so the
-- NOT NULL + UNIQUE constraints apply cleanly on non-empty databases.
-- New code always sets line_no explicitly (1 = DEBIT source, 2 = CREDIT dest).
UPDATE ledger_entries le SET line_no = ranked.rn
FROM (
    SELECT id, ROW_NUMBER() OVER (PARTITION BY transaction_id ORDER BY created_at, id) AS rn
    FROM ledger_entries WHERE line_no IS NULL
) ranked
WHERE le.id = ranked.id;

ALTER TABLE ledger_entries ALTER COLUMN line_no SET NOT NULL;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'uq_ledger_entries_tx_line'
    ) THEN
        ALTER TABLE ledger_entries
            ADD CONSTRAINT uq_ledger_entries_tx_line UNIQUE (transaction_id, line_no);
    END IF;
END $$;
