package com.vtempe.server.features.entitlement.data.repo

import com.vtempe.server.features.entitlement.domain.model.PaymentSource
import com.vtempe.server.features.entitlement.domain.port.EntitlementRecord
import com.vtempe.server.features.entitlement.domain.port.EntitlementRepository
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

/**
 * Used in two places: unit tests, and as the DI fallback when DATABASE_URL isn't configured
 * (see DatabaseConnector). In the fallback case grants don't survive a server restart/redeploy —
 * that's an accepted, logged trade-off for "still boots on a fresh checkout with no DB set up",
 * not a real production entitlement store.
 *
 * [recordPaymentAndExtend] runs under one lock, so concurrent payments extend one after the
 * other exactly like the per-user row lock of [ExposedEntitlementRepository].
 */
class InMemoryEntitlementRepository : EntitlementRepository {
    private val entitlements = ConcurrentHashMap<String, EntitlementRecord>()
    private val paymentIds = ConcurrentHashMap.newKeySet<String>()
    private val paymentLock = Mutex()

    override suspend fun find(userId: String): EntitlementRecord? = entitlements[userId]

    override suspend fun grantUntilAtLeast(userId: String, expiresAt: Instant, source: PaymentSource, externalRef: String?) {
        entitlements.merge(
            userId,
            EntitlementRecord(userId, expiresAt, source, externalRef)
        ) { existing, new -> if (existing.expiresAt >= new.expiresAt) existing else new }
    }

    override suspend fun revoke(userId: String) {
        entitlements.remove(userId)
    }

    override suspend fun recordPaymentAndExtend(
        externalId: String,
        userId: String,
        source: PaymentSource,
        amountMinor: Long,
        currency: String,
        rawPayload: String,
        newExpiry: (currentExpiry: Instant?) -> Instant
    ): Instant? = paymentLock.withLock {
        if (!paymentIds.add(externalId)) return@withLock null
        val current = entitlements[userId]?.expiresAt
        val target = newExpiry(current).let { if (current != null && current > it) current else it }
        entitlements[userId] = EntitlementRecord(userId, target, source, externalId)
        target
    }
}
