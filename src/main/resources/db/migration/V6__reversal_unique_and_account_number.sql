-- V6: P0 double-reversal guard + human 12-digit account number for P2P
-- 1) Each original tx can only be reversed once (DB is final arbiter, fixes check-then-insert race).
-- 2) wallets.account_number CHAR(12): user types this (not UUID) as P2P recipient.

-- 1) One reversal per original transaction (NULLs allowed multiple times in Postgres UNIQUE).
ALTER TABLE IF EXISTS ledger_transactions
    ADD CONSTRAINT uq_reversal_of_transaction UNIQUE (reversal_of_transaction_id);

-- 2) Account number column (nullable first for backfill).
ALTER TABLE IF EXISTS wallets
    ADD COLUMN IF NOT EXISTS account_number VARCHAR(12);

-- Fixed numbers for system wallets (easy to recognize, never typed by users).
UPDATE wallets SET account_number = '882000000001'
WHERE id = '00000000-0000-0000-0000-000000000001' AND account_number IS NULL;
UPDATE wallets SET account_number = '882000000002'
WHERE id = '00000000-0000-0000-0000-000000000002' AND account_number IS NULL;
UPDATE wallets SET account_number = '882000000003'
WHERE id = '00000000-0000-0000-0000-000000000003' AND account_number IS NULL;

-- Backfill user wallets: prefix 8820 + 8 random digits.
UPDATE wallets
SET account_number = '8820' || lpad((floor(random() * 100000000))::int::text, 8, '0')
WHERE account_number IS NULL;

-- Fix rare random collisions among backfilled rows.
UPDATE wallets w SET account_number = '8820' || lpad((floor(random() * 100000000))::int::text, 8, '0')
WHERE EXISTS (
    SELECT 1 FROM wallets x
    WHERE x.account_number = w.account_number AND x.ctid < w.ctid
);

ALTER TABLE IF EXISTS wallets
    ALTER COLUMN account_number SET NOT NULL;

ALTER TABLE IF EXISTS wallets
    ADD CONSTRAINT chk_wallets_account_number CHECK (account_number ~ '^[0-9]{12}$');

ALTER TABLE IF EXISTS wallets
    ADD CONSTRAINT uq_wallets_account_number UNIQUE (account_number);

CREATE INDEX IF NOT EXISTS idx_wallets_account_number ON wallets(account_number);
