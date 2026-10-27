-- ===================================================
-- V7__add_employee_fields_to_users.sql
-- Add employee profile fields to users table.
--
-- Columns are nullable initially to avoid migration issues
-- with existing data. Application layer enforces NOT NULL
-- for new employees. A future migration can backfill and
-- add NOT NULL constraints if needed.
-- ===================================================

ALTER TABLE users
    ADD COLUMN username VARCHAR(100) NULL,
    ADD COLUMN email VARCHAR(255) NULL,
    ADD COLUMN first_name VARCHAR(100) NULL,
    ADD COLUMN last_name VARCHAR(100) NULL;

-- Index for email lookups (optional, useful for login/queries)
CREATE INDEX idx_users_email
    ON users (email)
    WHERE email IS NOT NULL;