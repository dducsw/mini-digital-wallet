-- V9: Contacts table for saved transfer recipients
CREATE TABLE IF NOT EXISTS contacts (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    owner_id VARCHAR(50) NOT NULL,
    account_number VARCHAR(14) NOT NULL,
    nickname VARCHAR(100) NOT NULL,
    is_favorite BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_contacts_owner_account UNIQUE (owner_id, account_number)
);

CREATE INDEX IF NOT EXISTS idx_contacts_owner ON contacts(owner_id);
