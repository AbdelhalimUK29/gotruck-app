CREATE TABLE companies
(
    id BIGSERIAL PRIMARY KEY,

    name VARCHAR(255) NOT NULL,

    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE users
(
    id BIGSERIAL PRIMARY KEY,

    keycloak_id UUID NOT NULL UNIQUE,

    company_id BIGINT NOT NULL,

    owner BOOLEAN NOT NULL DEFAULT FALSE,

    super_admin BOOLEAN NOT NULL DEFAULT FALSE,

    created_by BIGINT NULL,

    updated_by BIGINT NULL,

    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_user_company
        FOREIGN KEY (company_id)
            REFERENCES companies(id)
            ON DELETE RESTRICT,

    CONSTRAINT fk_user_created_by
        FOREIGN KEY (created_by)
            REFERENCES users(id)
            ON DELETE RESTRICT,

    CONSTRAINT fk_user_updated_by
        FOREIGN KEY (updated_by)
            REFERENCES users(id)
            ON DELETE RESTRICT
);

CREATE INDEX idx_users_company
    ON users(company_id);

CREATE INDEX idx_users_created_by
    ON users(created_by);

CREATE INDEX idx_users_updated_by
    ON users(updated_by);