package com.project.goTruck.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

/**
 * Durable outbox row for the company provisioning workflow.
 *
 * Inserted atomically in the same database transaction as the company
 * aggregate (company, owner, features, idempotency key). A background
 * worker later claims the row via a lease and performs the remote
 * Keycloak update, retrying with exponential backoff and jitter, and
 * triggering compensation when the retry budget is exhausted.
 *
 * Mapped to company_provisioning_outbox (V5__company_provisioning_outbox.sql).
 * Intentionally no association to Company: compensation deletes the local
 * company while this row is retained as durable provisioning/audit history.
 *
 * The {@code version} column doubles as the fencing token: workers claim a
 * row by bumping the version, and every subsequent state transition must
 * carry the matching version or fail optimistically.
 */
@Entity
@Table(name = "company_provisioning_outbox")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CompanyProvisioningOutbox {

    /** Sole event type currently supported by the CHECK constraint. */
    public static final String EVENT_UPDATE_KEYCLOAK_COMPANY_ID =
            "UPDATE_KEYCLOAK_COMPANY_ID";

    /** Mirrors the max_attempts column default in V5. */
    public static final int DEFAULT_MAX_ATTEMPTS = 8;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "company_id", nullable = false)
    private Long companyId;

    @Column(name = "keycloak_user_id", nullable = false)
    private UUID keycloakUserId;

    @Column(name = "event_type", nullable = false, length = 64)
    private String eventType;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private ProvisioningStatus status;

    @Column(name = "attempt_count", nullable = false)
    private int attemptCount;

    @Column(name = "max_attempts", nullable = false)
    private int maxAttempts;

    @Column(name = "next_attempt_at", nullable = false)
    private Instant nextAttemptAt;

    @Column(name = "lease_until")
    private Instant leaseUntil;

    @Column(name = "lock_token")
    private UUID lockToken;

    @Column(name = "locked_by", length = 255)
    private String lockedBy;

    @Column(name = "last_error", length = 2000)
    private String lastError;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    /**
     * Optimistic-locking version; also serves as the fencing token for
     * lease-based claiming. Bulk claim queries must increment it manually
     * because @Modifying statements bypass this mechanism.
     */
    @Version
    @Column(name = "version", nullable = false)
    private long version;

    /**
     * Creates the initial PENDING event for a freshly persisted company.
     * This is the only sanctioned production path, guaranteeing the
     * defaults required by the table CHECK constraints.
     *
     * @param companyId      local database id of the created company
     * @param keycloakUserId Keycloak user id of the company owner
     * @param firstAttemptAt earliest instant the worker may claim the row
     */
    public static CompanyProvisioningOutbox newPendingEvent(
            Long companyId,
            UUID keycloakUserId,
            Instant firstAttemptAt
    ) {
        return CompanyProvisioningOutbox.builder()
                .companyId(companyId)
                .keycloakUserId(keycloakUserId)
                .eventType(EVENT_UPDATE_KEYCLOAK_COMPANY_ID)
                .status(ProvisioningStatus.PENDING)
                .attemptCount(0)
                .maxAttempts(DEFAULT_MAX_ATTEMPTS)
                .nextAttemptAt(firstAttemptAt)
                .build();
    }

    @PrePersist
    protected void onCreate() {
        Instant now = Instant.now();
        if (createdAt == null) {
            createdAt = now;
        }
        updatedAt = now;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }
}
