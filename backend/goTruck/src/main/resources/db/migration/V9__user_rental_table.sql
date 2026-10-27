-- ===================================================
-- V9__user_rental_table.sql
-- Create user_rentals join table for many-to-many User <-> Rental
-- ===================================================

CREATE TABLE user_rentals
(
    id         BIGSERIAL PRIMARY KEY,
    user_id    BIGINT NOT NULL,
    rental_id  BIGINT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_user_rental_user
        FOREIGN KEY (user_id)
            REFERENCES users(id)
            ON DELETE CASCADE,

    CONSTRAINT fk_user_rental_rental
        FOREIGN KEY (rental_id)
            REFERENCES rentals(id)
            ON DELETE CASCADE,

    CONSTRAINT uq_user_rental
        UNIQUE (user_id, rental_id)
);

CREATE INDEX idx_user_rentals_user ON user_rentals(user_id);
CREATE INDEX idx_user_rentals_rental ON user_rentals(rental_id);