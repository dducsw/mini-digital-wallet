-- V11: role-based authorization. Every existing/registered user is USER by default;
-- ADMIN gates the /api/v1/admin/** ops endpoints (e.g. reconciliation triggers).
-- Promote manually: UPDATE users SET role = 'ADMIN' WHERE username = '<ops-user>';

ALTER TABLE users ADD COLUMN IF NOT EXISTS role VARCHAR(20) NOT NULL DEFAULT 'USER';

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'chk_users_role') THEN
        ALTER TABLE users ADD CONSTRAINT chk_users_role CHECK (role IN ('USER', 'ADMIN'));
    END IF;
END $$;
