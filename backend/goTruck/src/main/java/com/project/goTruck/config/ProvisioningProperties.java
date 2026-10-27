package com.project.goTruck.config;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.net.InetAddress;
import java.time.Duration;
import java.util.UUID;

/**
 * Tuning knobs for the company provisioning outbox worker.
 *
 * All values can be overridden through relaxed binding, e.g.
 * APP_PROVISIONING_POLL_INTERVAL, APP_PROVISIONING_BATCH_SIZE,
 * APP_PROVISIONING_LEASE_DURATION or APP_PROVISIONING_WORKER_ID.
 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "app.provisioning")
public class ProvisioningProperties {

    /**
     * Delay between outbox polling cycles. Kept small so freshly
     * created companies are provisioned quickly, while the fixed
     * delay (not a rate) prevents overlapping cycles piling up when
     * Keycloak is slow.
     */
    private Duration pollInterval = Duration.ofSeconds(5);

    /**
     * Delay before the first polling cycle after startup. Gives
     * dependent infrastructure (database, Keycloak) a moment to settle
     * before the first poll; applies to both the outbox poller and the
     * retention cleanup.
     */
    private Duration initialDelay = Duration.ofSeconds(10);

    /**
     * Delay between retention cleanup runs of terminal outbox events.
     * Kept slow on purpose: the retention service itself no-ops when
     * nothing is due, so the frequency only affects how quickly
     * terminal events are purged after the retention window.
     */
    private Duration retentionInterval = Duration.ofHours(1);

    /**
     * Maximum number of events claimed per polling cycle. Bounded so a
     * large backlog can never monopolize a single worker iteration.
     */
    private int batchSize = 10;

    /**
     * Validity of a worker lease. Must comfortably exceed the slowest
     * expected Keycloak call (including its HTTP timeouts); once the
     * lease expires the event is reclaimed by another worker.
     */
    private Duration leaseDuration = Duration.ofMinutes(2);

    /**
     * Base delay of the exponential retry backoff. The delay after the
     * n-th failed attempt is backoffBase * 2^(n-1), capped at
     * backoffMax, with equal jitter applied (half fixed, half random)
     * to prevent thundering-herd retries against a recovering
     * Keycloak.
     */
    private Duration backoffBase = Duration.ofSeconds(2);

    /**
     * Ceiling of the exponential retry backoff.
     */
    private Duration backoffMax = Duration.ofMinutes(5);

    /**
     * HTTP connect timeout for all Keycloak admin calls.
     */
    private Duration httpConnectTimeout = Duration.ofSeconds(5);

    /**
     * HTTP read timeout for all Keycloak admin calls. Together with
     * the connect timeout and the token fetch it must stay well below
     * leaseDuration so a slow Keycloak cannot silently expire a lease
     * while the call is still in flight.
     */
    private Duration httpReadTimeout = Duration.ofSeconds(15);

    /**
     * How long terminal events (SUCCEEDED / COMPENSATED /
     * FAILED_MANUAL) are kept before the retention cleanup deletes
     * them. Zero or negative disables retention (keep forever).
     */
    private Duration retention = Duration.ofDays(30);

    /**
     * Identifier of this worker instance, written to lock_by for
     * diagnostics. Defaults to hostname plus a random suffix so each
     * process (and restart) is distinguishable; a blank configured
     * value falls back to the default.
     */
    @Setter(AccessLevel.NONE)
    private String workerId = defaultWorkerId();

    public void setWorkerId(String workerId) {
        if (workerId != null && !workerId.isBlank()) {
            this.workerId = workerId;
        }
    }

    private static String defaultWorkerId() {
        String host;
        try {
            host = InetAddress.getLocalHost().getHostName();
        } catch (Exception e) {
            host = "unknown-host";
        }
        return host + "-" + UUID.randomUUID().toString().substring(0, 8);
    }
}
