package com.project.goTruck.service.company;

import com.project.goTruck.config.ProvisioningProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.SchedulingConfigurer;
import org.springframework.scheduling.config.FixedDelayTask;
import org.springframework.scheduling.config.ScheduledTaskRegistrar;
import org.springframework.stereotype.Component;

/**
 * Polls the company provisioning outbox on a fixed delay.
 *
 * Each cycle first releases expired leases (so a crashed worker can
 * never strand an event), then claims and processes due Keycloak
 * updates and due compensations, and finally refreshes the status
 * gauges. Fixed delay — not fixed rate — so a slow Keycloak call can
 * never cause cycles to pile up and overlap.
 *
 * Retention cleanup of terminal events runs on its own much slower
 * schedule (see cleanupTerminalEvents).
 *
 * Tasks are registered programmatically via SchedulingConfigurer with
 * java.time.Duration values from ProvisioningProperties instead of
 * @Scheduled *String attributes: the Duration-based relaxed binding
 * accepts all common styles (5s, PT5S, 5000ms), whereas the
 * annotation string parser on the Spring Framework version in use
 * only understands plain milliseconds or strict ISO-8601.
 *
 * Can be disabled entirely (e.g. for a dedicated API-only replica)
 * with app.provisioning.scheduler-enabled=false.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(
        prefix = "app.provisioning",
        name = "scheduler-enabled",
        havingValue = "true",
        matchIfMissing = true
)
public class CompanyProvisioningScheduler implements SchedulingConfigurer {

    private final CompanyProvisioningClaimService claimService;
    private final CompanyProvisioningWorker provisioningWorker;
    private final CompanyCompensationWorker compensationWorker;
    private final CompanyProvisioningMetrics metrics;
    private final CompanyProvisioningRetentionService retentionService;
    private final ProvisioningProperties properties;

    @Override
    public void configureTasks(ScheduledTaskRegistrar taskRegistrar) {
        taskRegistrar.addFixedDelayTask(new FixedDelayTask(
                this::pollOutbox,
                properties.getPollInterval(),
                properties.getInitialDelay()
        ));
        taskRegistrar.addFixedDelayTask(new FixedDelayTask(
                this::cleanupTerminalEvents,
                properties.getRetentionInterval(),
                properties.getInitialDelay()
        ));
    }

    public void pollOutbox() {
        try {
            claimService.releaseExpiredLeases();
            claimService.claimAndProcessKeycloakUpdates(
                    provisioningWorker::processKeycloakUpdate
            );
            claimService.claimAndProcessCompensations(
                    compensationWorker::processCompensation
            );
        } catch (Exception e) {
            // Never let an exception kill the scheduling thread.
            log.error("Provisioning polling cycle failed", e);
        } finally {
            metrics.refreshStatusGauges();
        }
    }

    /**
     * Deletes terminal events past the retention window. Runs hourly;
     * the retention service itself no-ops when retention is disabled
     * or nothing is due, so the frequency is cheap.
     */
    public void cleanupTerminalEvents() {
        try {
            retentionService.cleanupExpiredEvents();
        } catch (Exception e) {
            // Never let an exception kill the scheduling thread.
            log.error("Provisioning retention cleanup failed", e);
        }
    }
}
