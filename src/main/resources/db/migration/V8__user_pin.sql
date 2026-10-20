-- V8: Add transaction PIN hash to users table
ALTER TABLE users ADD COLUMN pin_hash VARCHAR(255);
