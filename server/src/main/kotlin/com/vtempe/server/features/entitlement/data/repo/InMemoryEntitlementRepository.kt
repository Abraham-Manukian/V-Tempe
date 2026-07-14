package com.vtempe.server.features.entitlement.data.repo

import com.vtempe.server.features.entitlement.domain.model.PaymentSource
import com.vtempe.server.features.entitlement.domain.port.EntitlementRecord
import com.vtempe.server.features.entitlement.domain.port.EntitlementRepository
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

/**
 * Used in two places: unit tests, and as the DI fallback when DATABASE_URL isn't configured
 * (see DatabaseFactory). In the fallback case grants don't survive a server restart/redeploy —
 * that's an accepted, logged trade-off for "still boots on a fresh checkout with no DB set up",
 * not a real production entitlement store.
 *
 * [recordPaymentAndGrant] here is NOT truly atomic (two sequential in-memory operations, not one
 * transaction) — acceptable only because neither `ConcurrentHashMap` operation involved can
 * throw, so the "payment recorded but grant never happened" failure mode this method exists to
 * prevent (see EntitlementRepository kdoc) is structurally unreachable for this implementation.
 * [ExposedEntitlementRepository] is where the atomicity contract actually has to be earned.
 */
class InMemoryEntitlementRepository : EntitlementRepository {
    private val entitlements = ConcurrentHashMap<String, EntitlementRecord>()
    private val paymentIds = ConcurrentHashMap.newKeySet<String>()

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

    override suspend fun recordPaymentAndGrant(
        externalId: String,
        userId: String,
        source: PaymentSource,
        amountMinor: Long,
        currency: String,
        rawPayload: String,
        expiresAt: Instant
    ): Boolean {
        if (!paymentIds.add(externalId)) return false
        grantUntilAtLeast(userId, expiresAt, source, externalRef = externalId)
        return true
    }
}
