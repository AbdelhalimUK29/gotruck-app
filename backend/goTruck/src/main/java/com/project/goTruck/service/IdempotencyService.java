package com.project.goTruck.service;

import com.project.goTruck.entity.IdempotencyKey;
import com.project.goTruck.repository.IdempotencyKeyRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class IdempotencyService {

    private final IdempotencyKeyRepository idempotencyKeyRepository;

    /**
     * Resolves the company id previously created under the given
     * idempotency key, if any. The caller enriches the replay response
     * with the live provisioning status from the outbox.
     */
    @Transactional(readOnly = true)
    public Optional<Long> findCompanyId(
            String idempotencyKey,
            UUID keycloakId
    ) {
        return idempotencyKeyRepository
                .findByIdempotencyKeyAndKeycloakId(idempotencyKey, keycloakId)
                .map(IdempotencyKey::getCompanyId);
    }

    /**
     * Stores the idempotency record. Uses Propagation.REQUIRED (the
     * default) so that, when invoked inside the company creation
     * transaction, the record commits atomically with the company
     * aggregate and the outbox row — or not at all.
     */
    @Transactional(propagation = Propagation.REQUIRED)
    public void storeResult(
            String idempotencyKey,
            UUID keycloakId,
            Long companyId
    ) {
        IdempotencyKey record = IdempotencyKey.builder()
                .idempotencyKey(idempotencyKey)
                .keycloakId(keycloakId)
                .companyId(companyId)
                .build();

        idempotencyKeyRepository.save(record);
    }
}
