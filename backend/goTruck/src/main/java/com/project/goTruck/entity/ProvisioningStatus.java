package com.project.goTruck.entity;

/**
 * Lifecycle states of a company provisioning outbox event.
 *
 * State machine:
 * <pre>
 * PENDING -> PROCESSING -> SUCCEEDED
 *                |-> RETRY -> PROCESSING (exponential backoff + jitter)
 *                |-> COMPENSATION_REQUIRED -> COMPENSATING -> COMPENSATED
 *                                                 |-> FAILED_MANUAL
 * </pre>
 *
 * Values must match the chk_company_provisioning_status CHECK constraint
 * in V5__company_provisioning_outbox.sql.
 */
public enum ProvisioningStatus {

    /**
     * Row inserted atomically in the same transaction as the company
     * aggregate; awaiting the first worker claim.
     */
    PENDING,

    /**
     * A worker holds the lease and is executing the Keycloak update.
     */
    PROCESSING,

    /**
     * Attempt failed; scheduled for a later retry with exponential
     * backoff and jitter.
     */
    RETRY,

    /**
     * Keycloak update succeeded. Terminal success state.
     */
    SUCCEEDED,

    /**
     * Retry budget exhausted or unrecoverable failure; the local company
     * records must be compensated.
     */
    COMPENSATION_REQUIRED,

    /**
     * A worker holds the lease and is deleting the local company aggregate.
     */
    COMPENSATING,

    /**
     * Local records removed. Terminal compensated state.
     */
    COMPENSATED,

    /**
     * Compensation itself failed. Terminal state requiring manual
     * reconciliation; never silently discarded.
     */
    FAILED_MANUAL;

    /**
     * @return true when the event reached a final state and will never be
     * scheduled again (used for response mapping and retention cleanup).
     */
    public boolean isTerminal() {
        return this == SUCCEEDED || this == COMPENSATED || this == FAILED_MANUAL;
    }
}
