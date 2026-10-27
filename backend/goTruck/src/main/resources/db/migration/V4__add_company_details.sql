-- ===================================================
-- V4__add_company_details.sql
-- Extend companies table with contact & location data
-- ===================================================

ALTER TABLE companies
    ADD COLUMN website  VARCHAR(255) NULL,
    ADD COLUMN email    VARCHAR(255) NOT NULL,
    ADD COLUMN location VARCHAR(255) NULL,
    ADD COLUMN city     VARCHAR(100) NULL,
    ADD COLUMN country  VARCHAR(100) NULL;
