package com.project.goTruck.service.company;

import com.project.goTruck.config.ProvisioningProperties;
import com.project.goTruck.entity.CompanyProvisioningOutbox;
import com.project.goTruck.entity.ProvisioningStatus;
import com.project.goTruck.keycloak.KeycloakAdminClient;
import com.project.goTruck.repository.CompanyProvisioningOutboxRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.SimpleTransactionStatus;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Verification coverage for the outbox worker behaviours that can be
 * asserted without a database or a Keycloak: the exponential backoff
 * with equal jitter, the fenced success path, the fenced failure path
 * with retry scheduling, and exception containment.
 */
@ExtendWith(MockitoExtension.class)
class CompanyProvisioningWorkerTest {

    @Mock
    private KeycloakAdminClient keycloakAdminClient;

    @Mock
    private CompanyProvisioningOutboxRepository outboxRepository;

    @Mock
    private PlatformTransactionManager transactionManager;

    @Mock
    private CompanyProvisioningMetrics metrics;

    private ProvisioningProperties properties;

    private CompanyProvisioningWorker worker;

    @BeforeEach
    void setUp() {
        properties = new ProvisioningProperties();
        properties.setBackoffBase(Duration.ofSeconds(1));
        properties.setBackoffMax(Duration.ofSeconds(64));

        // TransactionTemplate executes callbacks synchronously when the
        // manager hands out a simple status and commits immediately.
        lenient().when(transactionManager.getTransaction(any()))
                .thenReturn(new SimpleTransactionStatus());

        worker = new CompanyProvisioningWorker(
                keycloakAdminClient,
                outboxRepository,
                properties,
                transactionManager,
                metrics
        );
    }

    @Test
    void backoffGrowsExponentiallyAndStaysWithinEqualJitterBounds() {
        long baseMillis = Duration.ofSeconds(1).toMillis();
        long maxMillis = Duration.ofSeconds(64).toMillis();

        for (int attempt = 1; attempt <= 10; attempt++) {
            Duration backoff = worker.computeBackoff(attempt);

            int exponent = Math.min(attempt - 1, 20);
            long exponential = Math.min(maxMillis, baseMillis << exponent);

            // Equal jitter: half fixed + random half.
            assertThat(backoff.toMillis())
                    .as("attempt %d", attempt)
                    .isGreaterThanOrEqualTo(exponential / 2)
                    .isLessThanOrEqualTo(exponential);
        }
    }

    @Test
    void backoffIsCappedAtMax() {
        properties.setBackoffMax(Duration.ofMillis(10));

        Duration backoff = worker.computeBackoff(50);

        assertThat(backoff.toMillis()).isBetween(5L, 10L);
    }

    @Test
    void successfulKeycloakCallRecordsFencedSuccess() {
        CompanyProvisioningOutbox event = claimedEvent();
        when(outboxRepository.completeKeycloakUpdate(
                eq(event.getId()),
                eq(event.getLockToken()),
                eq(event.getVersion())
        )).thenReturn(1);

        worker.processKeycloakUpdate(event);

        verify(keycloakAdminClient).updateUserCompanyId(
                eq(event.getKeycloakUserId()),
                eq(String.valueOf(event.getCompanyId()))
        );
        verify(outboxRepository).completeKeycloakUpdate(
                event.getId(),
                event.getLockToken(),
                event.getVersion()
        );
    }

    @Test
    void failingKeycloakCallSchedulesFencedRetry() {
        CompanyProvisioningOutbox event = claimedEvent();
        when(outboxRepository.failKeycloakUpdate(
                eq(event.getId()),
                eq(event.getLockToken()),
                eq(event.getVersion()),
                any(Instant.class),
                anyString()
        )).thenReturn(1);
        doThrow(new RuntimeException("keycloak down"))
                .when(keycloakAdminClient)
                .updateUserCompanyId(any(UUID.class), anyString());

        worker.processKeycloakUpdate(event);

        verify(outboxRepository).failKeycloakUpdate(
                eq(event.getId()),
                eq(event.getLockToken()),
                eq(event.getVersion()),
                any(Instant.class),
                contains("keycloak down")
        );
    }

    @Test
    void exceptionsFromKeycloakNeverPropagate() {
        CompanyProvisioningOutbox event = claimedEvent();
        when(outboxRepository.failKeycloakUpdate(
                any(Long.class),
                any(UUID.class),
                any(Long.class),
                any(Instant.class),
                anyString()
        )).thenReturn(0);
        doThrow(new RuntimeException("boom"))
                .when(keycloakAdminClient)
                .updateUserCompanyId(any(UUID.class), anyString());

        // Must not throw: a failed outcome is data, not an error.
        worker.processKeycloakUpdate(event);
    }

    private CompanyProvisioningOutbox claimedEvent() {
        CompanyProvisioningOutbox event =
                CompanyProvisioningOutbox.newPendingEvent(
                        42L,
                        UUID.randomUUID(),
                        Instant.now()
                );
        event.setId(1L);
        // Simulate the claim: fresh lock token, bumped version.
        event.setLockToken(UUID.randomUUID());
        event.setVersion(1L);
        event.setAttemptCount(1);
        event.setStatus(ProvisioningStatus.PROCESSING);
        return event;
    }
}
