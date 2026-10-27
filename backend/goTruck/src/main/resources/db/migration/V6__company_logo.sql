-- ===================================================
-- V6__company_logo.sql
-- Add the current company logo object key.
--
-- The column stores ONLY the current public-bucket object
-- key (e.g. company/25/logo/<uuid>.webp). Temporary upload
-- state lives in Redis + the temp bucket — never here.
-- ===================================================

ALTER TABLE companies
    ADD COLUMN logo VARCHAR(512) NULL;
