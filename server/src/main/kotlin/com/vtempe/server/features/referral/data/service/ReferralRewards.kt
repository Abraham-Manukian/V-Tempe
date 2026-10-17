package com.vtempe.server.features.referral.data.service

import com.vtempe.server.features.entitlement.domain.model.PaymentSource
import com.vtempe.server.features.entitlement.domain.model.VerifiedPayment
import com.vtempe.server.features.entitlement.domain.model.VerifiedRefund
import com.vtempe.server.features.entitlement.domain.port.EntitlementRepository
import com.vtempe.server.features.entitlement.domain.port.PaymentObserver
import com.vtempe.server.features.referral.domain.model.LedgerEntry
import com.vtempe.server.features.referral.domain.model.LedgerKind
import com.vtempe.server.features.referral.domain.model.ReferralPolicy
import com.vtempe.server.features.referral.domain.port.ReferralRepository
import org.slf4j.LoggerFactory
import java.time.Instant

/**
 * Turns verified payments and refunds into referral balance movements. Registered as the
 * [PaymentObserver] of EntitlementService, so every payment provider feeds it, on every delivery,
 * duplicates included: each step is idempotent, and a delivery that recorded the payment but failed
 * before this step is completed by the provider's retry.
 */
class ReferralRewards(
    private val repository: ReferralRepository,
    private val payments: EntitlementRepository,
    private val policy: ReferralPolicy,
    private val clock: () -> Instant = Instant::now,
) : PaymentObserver {
    private val logger = LoggerFactory.getLogger(ReferralRewards::class.java)

    /**
     * Only the invited user's FIRST provider payment earns, and the payments ledger decides which
     * one that is - not when the code was entered - so a later payment never earns, even when the
     * code was entered while the first payment was being processed. The share is taken of what is
     * left after refunds the provider already reports, so a payment refunded before this step ran
     * earns nothing. `accrual:<invitee>` is unique: redeliveries add nothing.
     */
    override suspend fun onPayment(payment: VerifiedPayment) {
        if (payment.source == PaymentSource.REFERRAL_BALANCE) return
        val attribution = repository.attributionOf(payment.userId) ?: return
        if (payments.firstProviderPaymentId(payment.userId) != payment.externalId) return
        val share = policy.shareOf(payment.amountMinor - payment.refundedMinor)
        if (share <= 0) return
        val now = clock()
        val added = repository.appendIfAbsent(
            LedgerEntry(
                userId = attribution.referrerId,
                kind = LedgerKind.ACCRUAL,
                sourceRef = "accrual:${payment.userId}",
                paymentExternalId = payment.externalId,
                inviteeId = payment.userId,
                currency = payment.currency,
                amountMinor = share,
                availableAt = now.plus(policy.hold),
                periodDays = null,
                completed = false,
                createdAt = now,
            )
        )
        if (added) logger.info("Referral: payment {} earned {} {} (minor) for the referrer, pending until {}",
            payment.externalId, share, payment.currency, now.plus(policy.hold))
    }

    /**
     * A verified refund of a payment that earned a share takes it back: all of it for a full
     * refund, the same percentage of the refunded amount for a partial one. The reversal shares its
     * accrual's availability date, so during the hold it cancels the pending share; after the hold
     * it lowers the available balance (which may go below zero and then blocks spending).
     */
    override suspend fun onRefund(refund: VerifiedRefund) {
        val reversal = repository.reverse(refund.paymentExternalId, refund.refundId, clock()) { accrual, alreadyReversed ->
            if (refund.full) accrual.amountMinor - alreadyReversed else policy.shareOf(refund.amountMinor)
        }
        if (reversal != null) logger.info("Referral: refund {} took back {} {} (minor) earned by payment {}",
            refund.refundId, -reversal.amountMinor, reversal.currency, refund.paymentExternalId)
    }
}
