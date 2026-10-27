package com.project.goTruck.service.company;

import com.project.goTruck.config.ProvisioningProperties;
import com.project.goTruck.entity.CompanyProvisioningOutbox;
import com.project.goTruck.entity.ProvisioningStatus;
import com.project.goTruck.repository.CompanyProvisioningOutboxRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * Retention cleanup for the company provisioning outbox.
 *
 * Terminal events (SUCCEEDED / COMPENSATED / FAILED_MANUAL) older than
 * the retention window are deleted periodically so the table stays
 * small and the partial indexes stay efficient. FAILED_MANUAL rows
 * get the same retention treatment as the rest once the underlying
 * incident is resolved — the incident tracker owns the history, the
 * outbox does not.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CompanyProvisioningRetentionService {

    /** Terminal statuses eligible for retention cleanup. */
    private static final List<ProvisioningStatus> TERMINAL_STATUSES =
            List.of(
                    ProvisioningStatus.SUCCEEDED,
                    ProvisioningStatus.COMPENSATED,
                    ProvisioningStatus.FAILED_MANUAL
            );

    /** Upper bound of rows examined per cleanup run. */
    private static final int CLEANUP_PAGE_SIZE = 1000;

    private final CompanyProvisioningOutboxRepository outboxRepository;
    private final ProvisioningProperties properties;
    private final PlatformTransactionManager transactionManager;

    /**
     * Deletes terminal events completed before the retention cutoff.
     * Retention is disabled by configuring app.provisioning.retention
     * as 0 or negative; everything is then kept forever.
     */
    public void cleanupExpiredEvents() {
        Duration retention = properties.getRetention();
        if (retention == null || retention.isZero()
                || retention.isNegative()) {
            return;
        }

        Instant cutoff = Instant.now().minus(retention);

        List<Long> expiredIds = outboxRepository
                .findByStatusInAndCompletedAtBefore(
                        TERMINAL_STATUSES,
                        cutoff,
                        PageRequest.ofSize(CLEANUP_PAGE_SIZE)
                )
                .stream()
                .map(CompanyProvisioningOutbox::getId)
                .toList();

        if (expiredIds.isEmpty()) {
            return;
        }

        newTransaction().executeWithoutResult(status ->
                outboxRepository.deleteAllByIdInBatch(expiredIds)
        );

        log.info(
                "Provisioning retention cleanup removed {} terminal "
                        + "event(s) completed before {} (retention={})",
                expiredIds.size(), cutoff, retention
        );
    }

    private TransactionTemplate newTransaction() {
        return new TransactionTemplate(transactionManager);
    }
}
