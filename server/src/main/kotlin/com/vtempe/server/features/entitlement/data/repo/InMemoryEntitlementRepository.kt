package com.vtempe.server.features.entitlement.data.repo

import com.vtempe.server.features.entitlement.domain.model.GrantWindow
import com.vtempe.server.features.entitlement.domain.model.PaymentSource
import com.vtempe.server.features.entitlement.domain.model.RefundOutcome
import com.vtempe.server.features.entitlement.domain.model.RefundResult
import com.vtempe.server.features.entitlement.domain.model.VerifiedRefund
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
 * Payments and refunds run under one lock, so they apply one after the other exactly like the
 * per-user row lock of [ExposedEntitlementRepository].
 */
class InMemoryEntitlementRepository : EntitlementRepository {
    private val entitlements = ConcurrentHashMap<String, EntitlementRecord>()
    private val payments = LinkedHashMap<String, Pair<String, PaymentSource>>() // externalId -> (userId, source), recording order
    private val windows = HashMap<String, Pair<String, GrantWindow>>() // externalId -> (userId, window)
    private val refunds = HashMap<String, RefundOutcome>()
    private val refundedByPayment = HashMap<String, Long>() // paymentExternalId -> total refunded
    private val paymentLock = Mutex()

    override suspend fun find(userId: String): EntitlementRecord? = entitlements[userId]

    override suspend fun grantUntilAtLeast(userId: String, expiresAt: Instant, source: PaymentSource, externalRef: String?) {
        entitlements.merge(
            userId,
            EntitlementRecord(userId, expiresAt, source, externalRef)
        ) { existing, new -> if (existing.expiresAt >= new.expiresAt) existing else new }
    }

    override suspend fun hasPayments(userId: String): Boolean = paymentLock.withLock { payments.values.any { it.first == userId } }

    override suspend fun refundedMinor(paymentExternalId: String): Long =
        paymentLock.withLock { refundedByPayment[paymentExternalId] ?: 0 }

    override suspend fun firstProviderPaymentId(userId: String): String? = paymentLock.withLock {
        payments.entries.firstOrNull { (_, v) -> v.first == userId && v.second != PaymentSource.REFERRAL_BALANCE }?.key
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
        grant: (currentExpiry: Instant?) -> GrantWindow
    ): Instant? = paymentLock.withLock {
        if (payments.putIfAbsent(externalId, userId to source) != null) return@withLock null
        val current = entitlements[userId]?.expiresAt
        val window = grant(current)
        windows[externalId] = userId to window
        val target = if (current != null && current > window.until) current else window.until
        entitlements[userId] = EntitlementRecord(userId, target, source, externalId)
        target
    }

    override suspend fun recordRefund(refund: VerifiedRefund, now: Instant): RefundResult = paymentLock.withLock {
        if (refund.refundId in refunds) return@withLock RefundResult(RefundOutcome.DUPLICATE)
        val userId = payments[refund.paymentExternalId]?.first
        val window = windows[refund.paymentExternalId]?.second
        val result = when {
            userId == null -> RefundResult(RefundOutcome.UNMATCHED_REVIEW)
            !refund.full -> RefundResult(RefundOutcome.PARTIAL_REVIEW, userId)
            window == null -> RefundResult(RefundOutcome.UNMATCHED_REVIEW, userId)
            else -> {
                val cut = RefundCut.of(window, now)
                windows[refund.paymentExternalId] = userId to cut.refundedWindow
                windows.entries
                    .filter { (id, entry) -> id != refund.paymentExternalId && entry.first == userId && cut.movesUp(entry.second) }
                    .forEach { it.setValue(userId to cut.movedUp(it.value.second)) }
                entitlements.computeIfPresent(userId) { _, record -> record.copy(expiresAt = record.expiresAt.minus(cut.removed)) }
                RefundResult(RefundOutcome.ACCESS_REDUCED, userId, cut.removed)
            }
        }
        refunds[refund.refundId] = result.outcome
        refundedByPayment.merge(refund.paymentExternalId, refund.amountMinor, Long::plus)
        result
    }
}
