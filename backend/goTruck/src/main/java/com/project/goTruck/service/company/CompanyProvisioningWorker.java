package com.project.goTruck.service.company;

import com.project.goTruck.config.ProvisioningProperties;
import com.project.goTruck.entity.CompanyProvisioningOutbox;
import com.project.goTruck.keycloak.KeycloakAdminClient;
import com.project.goTruck.repository.CompanyProvisioningOutboxRepository;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Executes claimed Keycloak provisioning events.
 *
 * The remote Keycloak call runs OUTSIDE any database transaction — the
 * lease acquired at claim time is the only concurrency guard. After
 * the call, the outcome is recorded with a fenced transition
 * (lock_token + version): if this worker's lease expired and the event
 * was reclaimed, the write affects zero rows and the zombie silently
 * stands down.
 *
 * Failures are retried with exponential backoff and equal jitter:
 *
 *   delay = min(backoffMax, backoffBase * 2^(attempt-1))
 *   jittered = delay/2 + random(0 .. delay/2)
 *
 * Equal jitter keeps half of the exponential growth while spreading
 * retries, preventing a thundering herd against a recovering
 * Keycloak. When the retry budget (max_attempts) is exhausted, the
 * event is flagged COMPENSATION_REQUIRED and the Phase 5 compensation
 * worker takes over.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CompanyProvisioningWorker {

    private final KeycloakAdminClient keycloakAdminClient;
    private final CompanyProvisioningOutboxRepository outboxRepository;
    private final ProvisioningProperties properties;
    private final PlatformTransactionManager transactionManager;
    private final CompanyProvisioningMetrics metrics;

    /**
     * Processes one claimed event: performs the Keycloak update, then
     * records the outcome with fencing. Exceptions never propagate —
     * a failed outcome is data, not an error, and one bad event must
     * not abort the rest of the batch.
     */
    public void processKeycloakUpdate(CompanyProvisioningOutbox event) {
        log.info(
                "Processing Keycloak update: eventId={} companyId={} user={} attempt={}/{}",
                event.getId(), event.getCompanyId(),
                event.getKeycloakUserId(), event.getAttemptCount(),
                event.getMaxAttempts()
        );

        Timer.Sample timer = metrics.startKeycloakUpdateTimer();
        try {
            keycloakAdminClient.updateUserCompanyId(
                    event.getKeycloakUserId(),
                    String.valueOf(event.getCompanyId())
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
                outboxRepository.completeKeycloakUpdate(
                        event.getId(),
                        event.getLockToken(),
                        event.getVersion()
                )
        );

        if (updated != null && updated > 0) {
            metrics.stopKeycloakUpdateTimer(timer, "succeeded");
            log.info(
                    "Keycloak update succeeded: eventId={} companyId={}",
                    event.getId(), event.getCompanyId()
            );
        } else {
            metrics.stopKeycloakUpdateTimer(timer, "fenced-out");
            log.warn(
                    "Keycloak update succeeded but this worker was fenced out "
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
        Instant nextAttemptAt = Instant.now()
                .plus(computeBackoff(event.getAttemptCount()));

        String lastError = summarize(cause);

        Integer updated = newTransaction().execute(status ->
                outboxRepository.failKeycloakUpdate(
                        event.getId(),
                        event.getLockToken(),
                        event.getVersion(),
                        nextAttemptAt,
                        lastError
                )
        );

        if (updated == null || updated == 0) {
            metrics.stopKeycloakUpdateTimer(timer, "fenced-out");
            log.warn(
                    "Keycloak update failed and this worker was fenced out "
                            + "(lease lost): eventId={} companyId={}",
                    event.getId(), event.getCompanyId(), cause
            );
            return;
        }

        if (event.getAttemptCount() >= event.getMaxAttempts()) {
            metrics.stopKeycloakUpdateTimer(timer, "compensation-required");
            log.error(
                    "Keycloak update retry budget exhausted, compensation "
                            + "required: eventId={} companyId={} attempts={} "
                            + "lastError={}",
                    event.getId(), event.getCompanyId(),
                    event.getAttemptCount(), lastError, cause
            );
        } else {
            metrics.stopKeycloakUpdateTimer(timer, "retry-scheduled");
            log.warn(
                    "Keycloak update failed, scheduled for retry: eventId={} "
                            + "companyId={} attempt={} nextAttemptAt={} "
                            + "lastError={}",
                    event.getId(), event.getCompanyId(),
                    event.getAttemptCount(), nextAttemptAt, lastError, cause
            );
        }
    }

    /**
     * Exponential backoff with equal jitter for the given (1-based)
     * attempt number. Package-private for direct testability.
     */
    java.time.Duration computeBackoff(int attempt) {
        long baseMillis = properties.getBackoffBase().toMillis();
        long maxMillis = properties.getBackoffMax().toMillis();

        // Cap the exponent to avoid overflow on very high attempt counts.
        int exponent = Math.min(attempt - 1, 20);
        long exponential = baseMillis << exponent;

        long delay = Math.min(maxMillis, exponential);
        long half = delay / 2;
        long jitter = ThreadLocalRandom.current().nextLong(half + 1);

        return java.time.Duration.ofMillis(half + jitter);
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
