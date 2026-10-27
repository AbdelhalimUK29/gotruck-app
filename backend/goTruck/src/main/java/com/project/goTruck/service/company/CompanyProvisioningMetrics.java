package com.project.goTruck.service.company;

import com.project.goTruck.entity.ProvisioningStatus;
import com.project.goTruck.repository.CompanyProvisioningOutboxRepository;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Micrometer metrics for the company provisioning outbox.
 *
 * Gauges: gotruck.provisioning.outbox.status{status=...} — number of
 * events per status, refreshed on every polling cycle. A rising RETRY
 * or COMPENSATION_REQUIRED gauge signals Keycloak degradation; any
 * non-zero FAILED_MANUAL requires manual reconciliation.
 *
 * Timers: gotruck.provisioning.keycloak-update and
 * gotruck.provisioning.compensation — execution durations of the
 * remote-facing workers, including outcome tags.
 */
@Slf4j
@Component
public class CompanyProvisioningMetrics {

    static final String OUTBOX_STATUS_METRIC =
            "gotruck.provisioning.outbox.status";
    static final String KEYCLOAK_TIMER_METRIC =
            "gotruck.provisioning.keycloak.update";
    static final String COMPENSATION_TIMER_METRIC =
            "gotruck.provisioning.compensation";

    private final CompanyProvisioningOutboxRepository outboxRepository;
    private final MeterRegistry meterRegistry;

    private final AtomicLong pending = new AtomicLong();
    private final AtomicLong processing = new AtomicLong();
    private final AtomicLong retry = new AtomicLong();
    private final AtomicLong succeeded = new AtomicLong();
    private final AtomicLong compensationRequired = new AtomicLong();
    private final AtomicLong compensating = new AtomicLong();
    private final AtomicLong compensated = new AtomicLong();
    private final AtomicLong failedManual = new AtomicLong();

    public CompanyProvisioningMetrics(
            CompanyProvisioningOutboxRepository outboxRepository,
            MeterRegistry meterRegistry
    ) {
        this.outboxRepository = outboxRepository;
        this.meterRegistry = meterRegistry;
    }

    @PostConstruct
    void registerGauges() {
        registerGauge(ProvisioningStatus.PENDING, pending);
        registerGauge(ProvisioningStatus.PROCESSING, processing);
        registerGauge(ProvisioningStatus.RETRY, retry);
        registerGauge(ProvisioningStatus.SUCCEEDED, succeeded);
        registerGauge(
                ProvisioningStatus.COMPENSATION_REQUIRED,
                compensationRequired
        );
        registerGauge(ProvisioningStatus.COMPENSATING, compensating);
        registerGauge(ProvisioningStatus.COMPENSATED, compensated);
        registerGauge(ProvisioningStatus.FAILED_MANUAL, failedManual);
    }

    /**
     * Refreshes all status gauges from a single repository pass. The
     * scheduler calls this once per polling cycle.
     */
    public void refreshStatusGauges() {
        try {
            pending.set(outboxRepository.countByStatus(
                    ProvisioningStatus.PENDING
            ));
            processing.set(outboxRepository.countByStatus(
                    ProvisioningStatus.PROCESSING
            ));
            retry.set(outboxRepository.countByStatus(
                    ProvisioningStatus.RETRY
            ));
            succeeded.set(outboxRepository.countByStatus(
                    ProvisioningStatus.SUCCEEDED
            ));
            compensationRequired.set(outboxRepository.countByStatus(
                    ProvisioningStatus.COMPENSATION_REQUIRED
            ));
            compensating.set(outboxRepository.countByStatus(
                    ProvisioningStatus.COMPENSATING
            ));
            compensated.set(outboxRepository.countByStatus(
                    ProvisioningStatus.COMPENSATED
            ));
            failedManual.set(outboxRepository.countByStatus(
                    ProvisioningStatus.FAILED_MANUAL
            ));
        } catch (Exception e) {
            // Metrics must never break the polling cycle.
            log.warn("Failed to refresh provisioning status gauges", e);
        }
    }

    public Timer.Sample startKeycloakUpdateTimer() {
        return Timer.start(meterRegistry);
    }

    public void stopKeycloakUpdateTimer(
            Timer.Sample sample,
            String outcome
    ) {
        stopTimer(sample, KEYCLOAK_TIMER_METRIC, outcome);
    }

    public Timer.Sample startCompensationTimer() {
        return Timer.start(meterRegistry);
    }

    public void stopCompensationTimer(
            Timer.Sample sample,
            String outcome
    ) {
        stopTimer(sample, COMPENSATION_TIMER_METRIC, outcome);
    }

    private void stopTimer(
            Timer.Sample sample,
            String metric,
            String outcome
    ) {
        if (sample == null) {
            return;
        }
        sample.stop(Timer.builder(metric)
                .description("Provisioning worker execution duration")
                .tag("outcome", outcome)
                .register(meterRegistry));
    }

    private void registerGauge(
            ProvisioningStatus status,
            AtomicLong value
    ) {
        Gauge.builder(OUTBOX_STATUS_METRIC, value, AtomicLong::doubleValue)
                .description("Company provisioning outbox events per status")
                .tag("status", status.name())
                .register(meterRegistry);
    }
}
