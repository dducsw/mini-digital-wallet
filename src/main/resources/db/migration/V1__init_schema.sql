-- V1 Initial Schema for Wallet Module

CREATE EXTENSION IF NOT EXISTS "pgcrypto";

-- Table: wallets
CREATE TABLE IF NOT EXISTS wallets (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id VARCHAR(64) NOT NULL,
    wallet_type VARCHAR(32) NOT NULL DEFAULT 'USER',
    currency VARCHAR(3) NOT NULL DEFAULT 'USD',
    available_balance BIGINT NOT NULL DEFAULT 0,
    ledger_balance BIGINT NOT NULL DEFAULT 0,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    -- Invariant: User wallet cannot be negative. System accounts can be negative.
    CONSTRAINT chk_wallet_available_balance CHECK (wallet_type = 'SYSTEM' OR available_balance >= 0),
    CONSTRAINT chk_wallet_ledger_balance CHECK (wallet_type = 'SYSTEM' OR ledger_balance >= 0),
    CONSTRAINT chk_wallet_type CHECK (wallet_type IN ('USER', 'SYSTEM')),
    CONSTRAINT chk_wallet_status CHECK (status IN ('ACTIVE', 'FROZEN', 'CLOSED')),
    CONSTRAINT uq_wallets_user_currency_type UNIQUE (user_id, currency, wallet_type)
);

CREATE INDEX IF NOT EXISTS idx_wallets_user_id ON wallets(user_id);
CREATE INDEX IF NOT EXISTS idx_wallets_wallet_type ON wallets(wallet_type);

-- Seed System Wallets (USD, minor units = cents)
INSERT INTO wallets (id, user_id, wallet_type, currency, available_balance, ledger_balance, status, version)
VALUES
    ('00000000-0000-0000-0000-000000000001', 'SYS_EXTERNAL_SETTLEMENT', 'SYSTEM', 'USD', 0, 0, 'ACTIVE', 0),
    ('00000000-0000-0000-0000-000000000002', 'SYS_CASH_IN_TRANSIT',       'SYSTEM', 'USD', 0, 0, 'ACTIVE', 0),
    ('00000000-0000-0000-0000-000000000003', 'SYS_FEE_REVENUE',          'SYSTEM', 'USD', 0, 0, 'ACTIVE', 0)
ON CONFLICT (id) DO NOTHING;

-- Table: payment_methods (PCI-DSS Tokenized)
CREATE TABLE IF NOT EXISTS payment_methods (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    owner_id VARCHAR(64) NOT NULL,
    method_type VARCHAR(20) NOT NULL,
    provider VARCHAR(20) NOT NULL,
    masked_number VARCHAR(20) NOT NULL,
    holder_name VARCHAR(100) NOT NULL,
    payment_token VARCHAR(128) NOT NULL,
    is_default BOOLEAN NOT NULL DEFAULT FALSE,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_payment_methods_owner ON payment_methods(owner_id);

