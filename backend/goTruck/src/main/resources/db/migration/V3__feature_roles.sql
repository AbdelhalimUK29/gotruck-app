-- ===================================================
-- V3__feature_roles.sql
-- Features reference table + company/user feature tables
-- ===================================================

-- 1. Static features reference table
CREATE TABLE features
(
    id         BIGSERIAL PRIMARY KEY,

    name       VARCHAR(50) NOT NULL UNIQUE,

    created_at TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Seed static features
INSERT INTO features (name)
VALUES ('RESERVATION'),
       ('CLIENTS'),
       ('TRUCKS'),
       ('LONG_TIME_RENTAL');

-- 2. Company features (which features a company has)
CREATE TABLE company_features
(
    id         BIGSERIAL PRIMARY KEY,

    company_id BIGINT    NOT NULL,
    feature_id BIGINT    NOT NULL,

    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_company_features_company
        FOREIGN KEY (company_id)
            REFERENCES companies (id)
            ON DELETE CASCADE,

    CONSTRAINT fk_company_features_feature
        FOREIGN KEY (feature_id)
            REFERENCES features (id)
            ON DELETE RESTRICT,

    CONSTRAINT uq_company_feature
        UNIQUE (company_id, feature_id)
);

CREATE INDEX idx_company_features_company
    ON company_features (company_id);

-- 3. User features (which features a user is granted)
CREATE TABLE user_features
(
    id         BIGSERIAL PRIMARY KEY,

    user_id    BIGINT    NOT NULL,
    feature_id BIGINT    NOT NULL,

    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_user_features_user
        FOREIGN KEY (user_id)
            REFERENCES users (id)
            ON DELETE CASCADE,

    CONSTRAINT fk_user_features_feature
        FOREIGN KEY (feature_id)
            REFERENCES features (id)
            ON DELETE RESTRICT,

    CONSTRAINT uq_user_feature
        UNIQUE (user_id, feature_id)
);

CREATE INDEX idx_user_features_user
    ON user_features (user_id);