CREATE TABLE idempotency_keys
(
    id              BIGSERIAL PRIMARY KEY,

    idempotency_key VARCHAR(255) NOT NULL UNIQUE,

    keycloak_id     UUID         NOT NULL,

    company_id      BIGINT       NOT NULL,

    created_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_idempotency_company
        FOREIGN KEY (company_id)
            REFERENCES companies (id)
            ON DELETE CASCADE
);

CREATE INDEX idx_idempotency_key
    ON idempotency_keys (idempotency_key);

CREATE INDEX idx_idempotency_keycloak_id
    ON idempotency_keys (keycloak_id);