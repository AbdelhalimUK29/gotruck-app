-- ===================================================
-- V10__add_fullname_to_users.sql
-- Add full_name column to users table for faster filtering.
--
-- Column is nullable initially to avoid migration issues
-- with existing data. Application layer computes and populates
-- full_name on persist. A future migration can backfill and
-- add NOT NULL constraint if needed.
-- ===================================================

ALTER TABLE users
    ADD COLUMN full_name VARCHAR(201) NULL;

-- Index for full name filtering
CREATE INDEX idx_users_full_name
    ON users (full_name)
    WHERE full_name IS NOT NULL;