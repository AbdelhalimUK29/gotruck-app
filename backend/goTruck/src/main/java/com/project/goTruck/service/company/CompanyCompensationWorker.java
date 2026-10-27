package com.project.goTruck.service.company;

import com.project.goTruck.entity.CompanyProvisioningOutbox;
import com.project.goTruck.repository.CompanyProvisioningOutboxRepository;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Executes claimed compensation events: removes the local company
 * aggregate that could not be provisioned in Keycloak.
 *
 * The deletion itself runs in the compensation service's own database
 * transaction, OUTSIDE the outbox claiming transaction — the lease is
 * the only concurrency guard. After the deletion, the outcome is
 * recorded with a fenced transition (lock_token + version): a zombie
 * worker whose lease expired and whose event was reclaimed silently
 * loses its write.
 *
 * The compensation itself is idempotent (deletes by id, no-ops when
 * the rows are already gone), so retries and racing workers are
 * always safe.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CompanyCompensationWorker {

    private final CompanyCompensationService companyCompensationService;
    private final CompanyProvisioningOutboxRepository outboxRepository;
    private final PlatformTransactionManager transactionManager;
    private final CompanyProvisioningMetrics metrics;

    /**
     * Processes one claimed compensation event: deletes the local
     * company aggregate, then records the outcome with fencing.
     * Exceptions never propagate — one bad event must not abort the
     * rest of the batch.
     */
    public void processCompensation(CompanyProvisioningOutbox event) {
        log.warn(
                "Processing compensation: eventId={} companyId={} user={} "
                        + "attempt={}/{} lastError={}",
                event.getId(), event.getCompanyId(),
                event.getKeycloakUserId(), event.getAttemptCount(),
                event.getMaxAttempts(), event.getLastError()
        );

        Timer.Sample timer = metrics.startCompensationTimer();
        try {
            companyCompensationService.compensate(
                    event.getCompanyId(),
                    event.getKeycloakUserId()
            );

            complete(event, timer);
        } catch (Exception e) {
            fail(event, e, timer);
        }
    }

    private void complete(
            CompanyProvisioningOutbox event,
            Timer.Sample timer
    ) {
        Integer updated = newTransaction().execute(status ->
                outboxRepository.completeCompensation(
                        event.getId(),
                        event.getLockToken(),
                        event.getVersion()
                )
        );

        if (updated != null && updated > 0) {
            metrics.stopCompensationTimer(timer, "succeeded");
            log.warn(
                    "Compensation succeeded: eventId={} companyId={}",
                    event.getId(), event.getCompanyId()
            );
        } else {
            metrics.stopCompensationTimer(timer, "fenced-out");
            log.warn(
                    "Compensation succeeded but this worker was fenced out "
                            + "(lease lost): eventId={} companyId={}",
                    event.getId(), event.getCompanyId()
            );
        }
    }

    private void fail(
            CompanyProvisioningOutbox event,
            Exception cause,
            Timer.Sample timer
    ) {
        String lastError = summarize(cause);

        Integer updated = newTransaction().execute(status ->
                outboxRepository.failCompensation(
                        event.getId(),
                        event.getLockToken(),
                        event.getVersion(),
                        lastError
                )
        );

        if (updated == null || updated == 0) {
            metrics.stopCompensationTimer(timer, "fenced-out");
            log.warn(
                    "Compensation failed and this worker was fenced out "
                            + "(lease lost): eventId={} companyId={}",
                    event.getId(), event.getCompanyId(), cause
            );
            return;
        }

        metrics.stopCompensationTimer(timer, "failed-manual");
        // failCompensation flags the event FAILED_MANUAL (terminal):
        // compensation failures are never silently discarded, human
        // reconciliation is required.
        log.error(
                "Compensation failed, manual reconciliation required: "
                        + "eventId={} companyId={} lastError={}",
                event.getId(), event.getCompanyId(), lastError, cause
        );
    }

    /**
     * Condenses a failure into the 2000-char last_error column.
     */
    private String summarize(Exception cause) {
        String message = cause.getClass().getSimpleName()
                + ": " + cause.getMessage();
        return message.length() > 2000
                ? message.substring(0, 2000)
                : message;
    }

    private TransactionTemplate newTransaction() {
        return new TransactionTemplate(transactionManager);
    }
}
