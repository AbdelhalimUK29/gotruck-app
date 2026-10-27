-- ===================================================
-- V8__rental_table.sql
-- Create rentals table for truck rental locations
-- ===================================================

CREATE TABLE rentals
(
    id              BIGSERIAL PRIMARY KEY,
    company_id      BIGINT       NOT NULL,
    name            VARCHAR(255) NULL,
    city            VARCHAR(100) NULL,
    location        VARCHAR(255) NULL,
    email           VARCHAR(255) NULL,
    logo            VARCHAR(512) NULL,
    deleted         BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_rental_company
        FOREIGN KEY (company_id)
            REFERENCES companies(id)
            ON DELETE RESTRICT
);

CREATE INDEX idx_rentals_company ON rentals(company_id);
CREATE INDEX idx_rentals_deleted ON rentals(deleted);