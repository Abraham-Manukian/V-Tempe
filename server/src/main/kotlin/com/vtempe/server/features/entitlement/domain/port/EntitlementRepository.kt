package com.vtempe.server.features.entitlement.domain.port

import com.vtempe.server.features.entitlement.domain.model.PaymentSource
import java.time.Instant

/** Raw persisted row — no status field, [com.vtempe.server.features.entitlement.domain.model.Entitlement]
 *  derives status from [expiresAt] at read time. */
data class EntitlementRecord(
    val userId: String,
    val expiresAt: Instant,
    val source: PaymentSource,
    val externalRef: String?
)

interface EntitlementRepository {
    suspend fun find(userId: String): EntitlementRecord?

    /** Upserts the user's entitlement. Only extends [expiresAt] forward — a renewal webhook
     *  arriving for a user who already has a later expiry (e.g. duplicate delivery, or a
     *  concurrent renewal from a second provider) must never shorten their access. */
    suspend fun grantUntilAtLeast(userId: String, expiresAt: Instant, source: PaymentSource, externalRef: String?)

    /** Immediately ends access — refund/chargeback/cancellation. */
    suspend fun revoke(userId: String)

    /**
     * Atomically records a provider payment (idempotent on [externalId]) AND grants until at
     * least [expiresAt] — both in one transaction, so a webhook can never end up in the state
     * "payment marked processed, but the grant it was supposed to cause never happened" (which
     * would permanently strand a paying user, since a retried delivery would then see the
     * payment as already-processed and skip granting again).
     *
     * Returns false if [externalId] was already recorded — the grant is NOT reapplied in that
     * case, since the original processing already applied it.
     */
    suspend fun recordPaymentAndGrant(
        externalId: String,
        userId: String,
        source: PaymentSource,
        amountMinor: Long,
        currency: String,
        rawPayload: String,
        expiresAt: Instant
    ): Boolean
}
