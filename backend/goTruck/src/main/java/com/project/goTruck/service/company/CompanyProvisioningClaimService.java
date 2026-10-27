package com.project.goTruck.service.company;

import com.project.goTruck.config.ProvisioningProperties;
import com.project.goTruck.entity.CompanyProvisioningOutbox;
import com.project.goTruck.repository.CompanyProvisioningOutboxRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.List;
import java.util.function.Consumer;

/**
 * Safe claiming layer for the company provisioning outbox.
 *
 * Claiming happens in a short transaction (claim + re-read); the
 * expensive remote Keycloak call is executed AFTER that transaction
 * commits, so no database transaction is ever held open across a
 * remote call. State transitions after processing are guarded by the
 * fencing token pair (lock_token, version) captured at claim time —
 * a zombie worker whose lease expired and whose event was reclaimed
 * by another worker silently loses its write.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CompanyProvisioningClaimService {

    private final CompanyProvisioningOutboxRepository outboxRepository;
    private final ProvisioningProperties properties;
    private final PlatformTransactionManager transactionManager;

    /**
     * Claims up to batchSize due PENDING/RETRY events and invokes the
     * processor for each claimed event, outside the claiming
     * transaction.
     */
    public void claimAndProcessKeycloakUpdates(
            Consumer<CompanyProvisioningOutbox> processor
    ) {
        List<CompanyProvisioningOutbox> claimed = claim(
                () -> outboxRepository.claimDueKeycloakUpdates(
                        Instant.now(),
                        Instant.now().plus(properties.getLeaseDuration()),
                        properties.getWorkerId(),
                        properties.getBatchSize()
                )
        );

        claimed.forEach(processor);
    }

    /**
     * Claims up to batchSize due COMPENSATION_REQUIRED events and
     * invokes the processor for each claimed event, outside the
     * claiming transaction.
     */
    public void claimAndProcessCompensations(
            Consumer<CompanyProvisioningOutbox> processor
    ) {
        List<CompanyProvisioningOutbox> claimed = claim(
                () -> outboxRepository.claimDueCompensations(
                        Instant.now(),
                        Instant.now().plus(properties.getLeaseDuration()),
                        properties.getWorkerId(),
                        properties.getBatchSize()
                )
        );

        claimed.forEach(processor);
    }

    /**
     * Releases events whose lease expired while PROCESSING or
     * COMPENSATING back to RETRY so they become immediately
     * reclaimable. Runs at the start of every polling cycle.
     */
    public void releaseExpiredLeases() {
        Integer released = newTransaction().execute(status ->
                outboxRepository.releaseExpiredLeases(Instant.now())
        );
        if (released != null && released > 0) {
            log.warn(
                    "Released {} expired provisioning lease(s) for reclaim",
                    released
            );
        }
    }

    /**
     * Executes the claim in a short transaction, then re-reads the
     * claimed rows. The re-read is essential: the claim UPDATE
     * generates a fresh lock_token and bumps the version, and those
     * values form the fencing pair every later transition must carry.
     * The re-read is safe outside the transaction because the rows are
     * now leased to this worker until lease_until.
     */
    private List<CompanyProvisioningOutbox> claim(
            java.util.function.Supplier<List<Long>> claimQuery
    ) {
        List<Long> claimedIds = newTransaction().execute(status ->
                claimQuery.get()
        );
        if (claimedIds == null || claimedIds.isEmpty()) {
            return List.of();
        }
        return outboxRepository.findAllById(claimedIds);
    }

    private TransactionTemplate newTransaction() {
        return new TransactionTemplate(transactionManager);
    }
}
