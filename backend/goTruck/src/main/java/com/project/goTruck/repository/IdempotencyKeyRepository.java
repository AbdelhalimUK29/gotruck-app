package com.project.goTruck.repository;

import com.project.goTruck.entity.IdempotencyKey;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface IdempotencyKeyRepository
        extends JpaRepository<IdempotencyKey, Long> {

    @Query("""
            SELECT i FROM IdempotencyKey i
            WHERE i.idempotencyKey = :idempotencyKey
            AND i.keycloakId = :keycloakId
            """)
    Optional<IdempotencyKey> findByIdempotencyKeyAndKeycloakId(
            @Param("idempotencyKey") String idempotencyKey,
            @Param("keycloakId") UUID keycloakId
    );
}