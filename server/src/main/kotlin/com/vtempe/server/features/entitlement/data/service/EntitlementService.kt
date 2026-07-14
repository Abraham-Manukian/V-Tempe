package com.vtempe.server.features.entitlement.data.service

import com.vtempe.server.features.entitlement.domain.model.Entitlement
import com.vtempe.server.features.entitlement.domain.model.EntitlementStatus
import com.vtempe.server.features.entitlement.domain.model.PaymentSource
import com.vtempe.server.features.entitlement.domain.port.EntitlementRepository
import java.time.Duration
import java.time.Instant

/**
 * Single place that decides whether a user has access — every provider (Google Play, App
 * Store, YooKassa) grants through here, every enforcement check reads through here. Status is
 * always derived from `expiresAt` at call time (see Entitlement kdoc), never stored directly.
 */
class EntitlementService(
    private val repository: EntitlementRepository,
    private val gracePeriod: Duration = Duration.ofDays(3),
    private val clock: () -> Instant = Instant::now
) {
    suspend fun current(userId: String): Entitlement {
        val record = repository.find(userId)
            ?: return Entitlement(userId, EntitlementStatus.EXPIRED, null, null, null)
        val now = clock()
        val status = when {
            now.isBefore(record.expiresAt) -> EntitlementStatus.ACTIVE
            now.isBefore(record.expiresAt.plus(gracePeriod)) -> EntitlementStatus.GRACE
            else -> EntitlementStatus.EXPIRED
        }
        return Entitlement(userId, status, record.expiresAt, record.source, record.externalRef)
    }

    suspend fun isActiveOrGrace(userId: String): Boolean =
        current(userId).status != EntitlementStatus.EXPIRED

    suspend fun grant(userId: String, expiresAt: Instant, source: PaymentSource, externalRef: String? = null) {
        repository.grantUntilAtLeast(userId, expiresAt, source, externalRef)
    }

    suspend fun revoke(userId: String) {
        repository.revoke(userId)
    }

    /** Atomically records the payment (idempotent on [externalId]) and grants until at least
     *  [expiresAt] — see EntitlementRepository kdoc for why this must be one atomic operation,
     *  not two separate calls. Returns false if this exact payment was already processed (the
     *  grant is NOT reapplied in that case). */
    suspend fun recordPaymentAndGrant(
        externalId: String,
        userId: String,
        source: PaymentSource,
        amountMinor: Long,
        currency: String,
        rawPayload: String,
        expiresAt: Instant
    ): Boolean = repository.recordPaymentAndGrant(externalId, userId, source, amountMinor, currency, rawPayload, expiresAt)
}
