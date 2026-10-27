CREATE TABLE company_provisioning_outbox
(
    id                  BIGSERIAL PRIMARY KEY,
    company_id          BIGINT       NOT NULL,
    keycloak_user_id    UUID         NOT NULL,
    event_type          VARCHAR(64)  NOT NULL DEFAULT 'UPDATE_KEYCLOAK_COMPANY_ID',
    status              VARCHAR(32)  NOT NULL DEFAULT 'PENDING',
    attempt_count       INTEGER      NOT NULL DEFAULT 0,
    max_attempts        INTEGER      NOT NULL DEFAULT 8,
    next_attempt_at     TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    lease_until         TIMESTAMPTZ  NULL,
    lock_token          UUID         NULL,
    locked_by           VARCHAR(255) NULL,
    last_error          VARCHAR(2000) NULL,
    created_at          TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    started_at          TIMESTAMPTZ  NULL,
    completed_at        TIMESTAMPTZ  NULL,
    version             BIGINT       NOT NULL DEFAULT 0,

    CONSTRAINT uk_company_provisioning_event
        UNIQUE (company_id, event_type),

    CONSTRAINT chk_company_provisioning_event_type
        CHECK (event_type IN ('UPDATE_KEYCLOAK_COMPANY_ID')),

    CONSTRAINT chk_company_provisioning_status
        CHECK (status IN (
            'PENDING',
            'PROCESSING',
            'RETRY',
            'SUCCEEDED',
            'COMPENSATION_REQUIRED',
            'COMPENSATING',
            'COMPENSATED',
            'FAILED_MANUAL'
        )),

    CONSTRAINT chk_company_provisioning_attempts
        CHECK (attempt_count >= 0 AND max_attempts > 0),

    CONSTRAINT chk_company_provisioning_lease
        CHECK (
            (status IN ('PROCESSING', 'COMPENSATING')
                AND lease_until IS NOT NULL
                AND lock_token IS NOT NULL
                AND locked_by IS NOT NULL)
            OR
            (status NOT IN ('PROCESSING', 'COMPENSATING'))
        ),

    CONSTRAINT chk_company_provisioning_completion
        CHECK (
            (status IN ('SUCCEEDED', 'COMPENSATED', 'FAILED_MANUAL')
                AND completed_at IS NOT NULL)
            OR
            (status NOT IN ('SUCCEEDED', 'COMPENSATED', 'FAILED_MANUAL'))
        )
);

-- Intentionally no foreign key to companies: compensation may delete the local
-- company while this row is retained as durable provisioning/audit history.

CREATE INDEX idx_company_provisioning_due
    ON company_provisioning_outbox (next_attempt_at, created_at, id)
    WHERE status IN ('PENDING', 'RETRY');

CREATE INDEX idx_company_provisioning_expired_lease
    ON company_provisioning_outbox (lease_until, id)
    WHERE status IN ('PROCESSING', 'COMPENSATING');

CREATE INDEX idx_company_provisioning_compensation_due
    ON company_provisioning_outbox (next_attempt_at, created_at, id)
    WHERE status = 'COMPENSATION_REQUIRED';

CREATE INDEX idx_company_provisioning_user_status
    ON company_provisioning_outbox (keycloak_user_id, status);

CREATE INDEX idx_company_provisioning_status_updated
    ON company_provisioning_outbox (status, updated_at);
