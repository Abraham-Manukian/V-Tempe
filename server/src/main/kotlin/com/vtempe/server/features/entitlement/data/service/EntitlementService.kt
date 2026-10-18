package com.vtempe.server.features.entitlement.data.service

import com.vtempe.server.features.entitlement.domain.model.Entitlement
import com.vtempe.server.features.entitlement.domain.model.EntitlementStatus
import com.vtempe.server.features.entitlement.domain.model.GrantWindow
import com.vtempe.server.features.entitlement.domain.model.PaymentSource
import com.vtempe.server.features.entitlement.domain.model.RefundResult
import com.vtempe.server.features.entitlement.domain.model.VerifiedRefund
import com.vtempe.server.features.entitlement.domain.model.VerifiedPayment
import com.vtempe.server.features.entitlement.domain.port.EntitlementRepository
import com.vtempe.server.features.entitlement.domain.port.PaymentObserver
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
    /** Referral rewards in production (ServerModule); nothing in tests that do not need it. */
    private val observer: PaymentObserver = PaymentObserver.NONE,
    private val clock: () -> Instant = Instant::now,
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

    suspend fun hasPayments(userId: String): Boolean = repository.hasPayments(userId)

    suspend fun revoke(userId: String) {
        repository.revoke(userId)
    }

    /**
     * Atomically records the payment (idempotent on [externalId]) and adds [period] to the user's
     * access: from the current expiry while it is active or in grace, from now once expired. The
     * current expiry is read inside the repository's transaction (VT-AUD-051) — see
     * EntitlementRepository kdoc. [refundedMinor] is the provider's refunded total at verification:
     * a payment already refunded in full keeps only its used part, like a refund after it. Returns
     * the new expiry, or null if this exact payment was already processed (the grant is NOT
     * reapplied in that case).
     */
    suspend fun recordPaymentAndExtend(
        externalId: String,
        userId: String,
        source: PaymentSource,
        amountMinor: Long,
        currency: String,
        rawPayload: String,
        period: Duration,
        refundedMinor: Long = 0,
    ): Instant? {
        val now = clock()
        return repository.recordPaymentAndExtend(externalId, userId, source, amountMinor, currency, rawPayload, refundedMinor, now) { current ->
            val base = if (current != null && now.isBefore(current.plus(gracePeriod))) current else now
            GrantWindow(from = base, until = base.plus(period))
        }
    }

    /**
     * The entry point for every provider's verified payment: records it and extends access (see
     * [recordPaymentAndExtend]), then tells the [PaymentObserver] on every delivery, duplicates
     * included, so a delivery that failed after the commit is completed by the provider's retry.
     */
    suspend fun recordVerifiedPayment(payment: VerifiedPayment, rawPayload: String, period: Duration): Instant? {
        val expiresAt = recordPaymentAndExtend(
            payment.externalId, payment.userId, payment.source, payment.amountMinor, payment.currency, rawPayload, period,
            refundedMinor = payment.refundedMinor,
        )
        observer.onPayment(payment)
        return expiresAt
    }

    /** Takes back what a verified refund paid for (see [EntitlementRepository.recordRefund]), then
     *  tells the [PaymentObserver], duplicates included, for the same reason as payments. */
    suspend fun applyRefund(refund: VerifiedRefund): RefundResult {
        val result = repository.recordRefund(refund, clock())
        observer.onRefund(refund)
        return result
    }

    /** See [EntitlementRepository.firstProviderPaymentId]. */
    suspend fun firstProviderPaymentId(userId: String): String? = repository.firstProviderPaymentId(userId)
}
