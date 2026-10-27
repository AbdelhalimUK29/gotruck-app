package com.project.goTruck.repository;

import com.project.goTruck.entity.CompanyProvisioningOutbox;
import com.project.goTruck.entity.ProvisioningStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Data access for the company provisioning outbox.
 *
 * Read-side mapping only: each scheduling query below is shaped to hit one
 * of the partial indexes created in V5__company_provisioning_outbox.sql.
 * The write-side claiming machinery (row locking with SKIP LOCKED, lease
 * acquisition, fencing-token validation) is intentionally NOT part of this
 * repository; it belongs to the worker layer.
 *
 * Callers must always pass a bounded {@link Pageable} (e.g.
 * {@code PageRequest.ofSize(batchSize)}) to the scheduling queries so a
 * backlog can never translate into an unbounded result set. Pass the
 * Pageable without a sort: ordering is fixed by the queries themselves to
 * match the index column order.
 */
public interface CompanyProvisioningOutboxRepository
        extends JpaRepository<CompanyProvisioningOutbox, Long> {

    // ------------------------------------------------------------------
    // Lookups (status polling, response mapping, idempotent replay)
    // ------------------------------------------------------------------

    /**
     * Finds the provisioning event of a given type for a company.
     * Backed by the unique constraint (company_id, event_type), so at most
     * one row can exist per pair.
     */
    Optional<CompanyProvisioningOutbox> findByCompanyIdAndEventType(
            Long companyId,
            String eventType
    );

    /**
     * Finds the provisioning event of a given type for a Keycloak user.
     * Serves status checks keyed by the authenticated user; uses
     * idx_company_provisioning_user_status.
     */
    Optional<CompanyProvisioningOutbox> findByKeycloakUserIdAndEventType(
            UUID keycloakUserId,
            String eventType
    );

    /**
     * Finds the LATEST provisioning event of a given type for a Keycloak
     * user, newest first. A user can attempt creation again after a
     * compensated attempt (the outbox row survives as audit history),
     * so status polling must resolve the most recent event.
     */
    Optional<CompanyProvisioningOutbox>
    findTopByKeycloakUserIdAndEventTypeOrderByIdDesc(
            UUID keycloakUserId,
            String eventType
    );

    // ------------------------------------------------------------------
    // Scheduling queries (each maps to a V5 partial index)
    // ------------------------------------------------------------------

    /**
     * Returns PENDING/RETRY events whose next attempt is due, oldest first.
     * Uses idx_company_provisioning_due.
     *
     * PENDING/RETRY rows never carry a lease (enforced by the
     * chk_company_provisioning_lease CHECK constraint), so no additional
     * lease filtering is required here.
     */
    @Query("""
            SELECT o FROM CompanyProvisioningOutbox o
            WHERE o.status IN (
                com.project.goTruck.entity.ProvisioningStatus.PENDING,
                com.project.goTruck.entity.ProvisioningStatus.RETRY
            )
            AND o.nextAttemptAt <= :now
            ORDER BY o.nextAttemptAt, o.createdAt, o.id
            """)
    List<CompanyProvisioningOutbox> findDueKeycloakUpdates(
            @Param("now") Instant now,
            Pageable pageable
    );

    /**
     * Returns COMPENSATION_REQUIRED events whose compensation is due,
     * oldest first. Uses idx_company_provisioning_compensation_due.
     */
    @Query("""
            SELECT o FROM CompanyProvisioningOutbox o
            WHERE o.status = com.project.goTruck.entity.ProvisioningStatus.COMPENSATION_REQUIRED
            AND o.nextAttemptAt <= :now
            ORDER BY o.nextAttemptAt, o.createdAt, o.id
            """)
    List<CompanyProvisioningOutbox> findDueCompensations(
            @Param("now") Instant now,
            Pageable pageable
    );

    /**
     * Returns PROCESSING/COMPENSATING events whose lease has expired,
     * i.e. rows claimed by a worker that died or stalled past its lease.
     * Uses idx_company_provisioning_expired_lease.
     *
     * A lease is valid strictly while lease_until is in the future, so
     * lease_until <= now means the row is reclaimable.
     */
    @Query("""
            SELECT o FROM CompanyProvisioningOutbox o
            WHERE o.status IN (
                com.project.goTruck.entity.ProvisioningStatus.PROCESSING,
                com.project.goTruck.entity.ProvisioningStatus.COMPENSATING
            )
            AND o.leaseUntil <= :now
            ORDER BY o.leaseUntil, o.id
            """)
    List<CompanyProvisioningOutbox> findExpiredLeases(
            @Param("now") Instant now,
            Pageable pageable
    );

    // ------------------------------------------------------------------
    // Retention & observability
    // ------------------------------------------------------------------

    /**
     * Returns terminal events (SUCCEEDED/COMPENSATED/FAILED_MANUAL) that
     * completed before the cutoff, for retention cleanup. Uses
     * idx_company_provisioning_status_updated.
     */
    List<CompanyProvisioningOutbox> findByStatusInAndCompletedAtBefore(
            Collection<ProvisioningStatus> statuses,
            Instant cutoff,
            Pageable pageable
    );

    /**
     * Counts events currently in the given status; used for metrics and
     * backlog monitoring.
     */
    long countByStatus(ProvisioningStatus status);

    // ------------------------------------------------------------------
    // Claiming (lease acquisition with FOR UPDATE SKIP LOCKED)
    // ------------------------------------------------------------------

    /**
     * Atomically claims up to :limit due PENDING/RETRY events by
     * transitioning them to PROCESSING and writing this worker's lease
     * (lock_token, locked_by, lease_until). Rows already locked by
     * another worker are skipped, so concurrent workers never claim the
     * same event and never block each other.
     *
     * The version column is incremented manually: @Modifying bulk
     * statements bypass the @Version mechanism, and the incremented
     * version acts as the fencing token for all subsequent transitions.
     *
     * @return ids of the events claimed by this worker
     */
    @Modifying
    @Query(value = """
            UPDATE company_provisioning_outbox
            SET status = 'PROCESSING',
                attempt_count = attempt_count + 1,
                lock_token = gen_random_uuid(),
                locked_by = :workerId,
                lease_until = :leaseUntil,
                started_at = COALESCE(started_at, CURRENT_TIMESTAMP),
                updated_at = CURRENT_TIMESTAMP,
                version = version + 1
            WHERE id IN (
                SELECT id FROM company_provisioning_outbox
                WHERE status IN ('PENDING', 'RETRY')
                  AND next_attempt_at <= :now
                ORDER BY next_attempt_at, created_at, id
                LIMIT :limit
                FOR UPDATE SKIP LOCKED
            )
            RETURNING id
            """, nativeQuery = true)
    List<Long> claimDueKeycloakUpdates(
            @Param("now") Instant now,
            @Param("leaseUntil") Instant leaseUntil,
            @Param("workerId") String workerId,
            @Param("limit") int limit
    );

    /**
     * Atomically claims up to :limit due COMPENSATION_REQUIRED events
     * by transitioning them to COMPENSATING and writing this worker's
     * lease. Same SKIP LOCKED semantics as
     * claimDueKeycloakUpdates.
     *
     * @return ids of the events claimed by this worker
     */
    @Modifying
    @Query(value = """
            UPDATE company_provisioning_outbox
            SET status = 'COMPENSATING',
                attempt_count = attempt_count + 1,
                lock_token = gen_random_uuid(),
                locked_by = :workerId,
                lease_until = :leaseUntil,
                started_at = COALESCE(started_at, CURRENT_TIMESTAMP),
                updated_at = CURRENT_TIMESTAMP,
                version = version + 1
            WHERE id IN (
                SELECT id FROM company_provisioning_outbox
                WHERE status = 'COMPENSATION_REQUIRED'
                  AND next_attempt_at <= :now
                ORDER BY next_attempt_at, created_at, id
                LIMIT :limit
                FOR UPDATE SKIP LOCKED
            )
            RETURNING id
            """, nativeQuery = true)
    List<Long> claimDueCompensations(
            @Param("now") Instant now,
            @Param("leaseUntil") Instant leaseUntil,
            @Param("workerId") String workerId,
            @Param("limit") int limit
    );

    /**
     * Releases events whose lease expired while in PROCESSING or
     * COMPENSATING, so a crashed or stalled worker can never strand an
     * event. PROCESSING rows return to RETRY (Keycloak worker picks
     * them up again); COMPENSATING rows return to COMPENSATION_REQUIRED
     * (compensation worker picks them up again) — never to RETRY, which
     * would wrongly re-attempt the Keycloak update for a company that
     * may already be compensated. The lease columns are cleared to
     * satisfy chk_company_provisioning_lease, and next_attempt_at is
     * reset to now so the event is immediately reclaimable.
     *
     * @return number of events released
     */
    @Modifying
    @Query(value = """
            UPDATE company_provisioning_outbox
            SET status = CASE
                    WHEN status = 'COMPENSATING'
                        THEN 'COMPENSATION_REQUIRED'
                    ELSE 'RETRY'
                END,
                lock_token = NULL,
                locked_by = NULL,
                lease_until = NULL,
                next_attempt_at = CURRENT_TIMESTAMP,
                last_error = 'lease expired; released for reclaim',
                updated_at = CURRENT_TIMESTAMP,
                version = version + 1
            WHERE status IN ('PROCESSING', 'COMPENSATING')
              AND lease_until <= :now
            """, nativeQuery = true)
    int releaseExpiredLeases(@Param("now") Instant now);

    // ------------------------------------------------------------------
    // Fenced state transitions (fencing-token guarded)
    // ------------------------------------------------------------------

    /**
     * Marks a claimed event SUCCEEDED, guarded by the fencing token:
     * the update only applies when the row is still leased to this
     * worker (lock_token match) AND the version still matches the one
     * observed at claim time. A zombie worker that lost its lease (the
     * row was reclaimed and re-claimed by someone else) is silently
     * fenced out — its write affects zero rows.
     *
     * @return 1 on success, 0 when fenced out
     */
    @Modifying
    @Query(value = """  
            UPDATE company_provisioning_outbox
            SET status = 'SUCCEEDED',       
                completed_at = CURRENT_TIMESTAMP,
                last_error = NULL,
                lock_token = NULL,
                locked_by = NULL,
                lease_until = NULL,
                updated_at = CURRENT_TIMESTAMP,
                version = version + 1
            WHERE id = :id
              AND lock_token = :lockToken
              AND version = :fence
            """, nativeQuery = true)
    int completeKeycloakUpdate(
            @Param("id") Long id,
            @Param("lockToken") UUID lockToken,
            @Param("fence") long fence
    );

    /**
     * Marks a claimed event as failed and schedules the next retry
     * (status RETRY) or flags it COMPENSATION_REQUIRED when the retry
     * budget is exhausted. Fenced by lock_token + version exactly like
     * completeKeycloakUpdate.
     *
     * @return 1 on success, 0 when fenced out
     */
    @Modifying
    @Query(value = """
            UPDATE company_provisioning_outbox
            SET status = CASE
                    WHEN attempt_count >= max_attempts
                        THEN 'COMPENSATION_REQUIRED'
                    ELSE 'RETRY'
                END,
                next_attempt_at = :nextAttemptAt,
                last_error = :lastError,
                lock_token = NULL,
                locked_by = NULL,
                lease_until = NULL,
                completed_at = CASE
                    WHEN attempt_count >= max_attempts
                        THEN CURRENT_TIMESTAMP
                    ELSE completed_at
                END,
                updated_at = CURRENT_TIMESTAMP,
                version = version + 1
            WHERE id = :id
              AND lock_token = :lockToken
              AND version = :fence
            """, nativeQuery = true)
    int failKeycloakUpdate(
            @Param("id") Long id,
            @Param("lockToken") UUID lockToken,
            @Param("fence") long fence,
            @Param("nextAttemptAt") Instant nextAttemptAt,
            @Param("lastError") String lastError
    );

    /**
     * Marks a claimed compensation event COMPENSATED (terminal),
     * fenced by lock_token + version.
     *
     * @return 1 on success, 0 when fenced out
     */
    @Modifying
    @Query(value = """
            UPDATE company_provisioning_outbox
            SET status = 'COMPENSATED',
                completed_at = CURRENT_TIMESTAMP,
                last_error = NULL,
                lock_token = NULL,
                locked_by = NULL,
                lease_until = NULL,
                updated_at = CURRENT_TIMESTAMP,
                version = version + 1
            WHERE id = :id
              AND lock_token = :lockToken
              AND version = :fence
            """, nativeQuery = true)
    int completeCompensation(
            @Param("id") Long id,
            @Param("lockToken") UUID lockToken,
            @Param("fence") long fence
    );

    /**
     * Marks a claimed compensation event FAILED_MANUAL (terminal,
     * requires human reconciliation), fenced by lock_token + version.
     *
     * @return 1 on success, 0 when fenced out
     */
    @Modifying
    @Query(value = """
            UPDATE company_provisioning_outbox
            SET status = 'FAILED_MANUAL',
                completed_at = CURRENT_TIMESTAMP,
                last_error = :lastError,
                lock_token = NULL,
                locked_by = NULL,
                lease_until = NULL,
                updated_at = CURRENT_TIMESTAMP,
                version = version + 1
            WHERE id = :id
              AND lock_token = :lockToken
              AND version = :fence
            """, nativeQuery = true)
    int failCompensation(
            @Param("id") Long id,
            @Param("lockToken") UUID lockToken,
            @Param("fence") long fence,
            @Param("lastError") String lastError
    );
}
