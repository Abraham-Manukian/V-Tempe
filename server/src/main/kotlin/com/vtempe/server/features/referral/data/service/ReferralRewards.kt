package com.vtempe.server.features.referral.data.service

import com.vtempe.server.features.entitlement.domain.model.PaymentSource
import com.vtempe.server.features.entitlement.domain.model.VerifiedPayment
import com.vtempe.server.features.entitlement.domain.model.VerifiedRefund
import com.vtempe.server.features.entitlement.domain.port.EntitlementRepository
import com.vtempe.server.features.entitlement.domain.port.PaymentObserver
import com.vtempe.server.features.referral.domain.model.LedgerEntry
import com.vtempe.server.features.referral.domain.model.LedgerKind
import com.vtempe.server.features.referral.domain.model.ReferralPolicy
import com.vtempe.server.features.referral.domain.model.settledShareMinor
import com.vtempe.server.features.referral.domain.port.ReferralRepository
import org.slf4j.LoggerFactory
import java.time.Instant

/**
 * Turns verified payments and refunds into referral balance movements. Registered as the
 * [PaymentObserver] of EntitlementService, so every payment provider feeds it, on every delivery,
 * duplicates included: each step is idempotent, and a delivery that recorded the payment but failed
 * before this step is completed by the provider's retry.
 *
 * Refunds are settled against a target (the share of what the user kept, from every refund the
 * payments ledger recorded), never applied as deltas: a refund noticed both by the payment step and
 * by its own notification, in either order or at the same time, is taken back exactly once.
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
     * code was entered while the first payment was being processed. `accrual:<invitee>` is unique:
     * redeliveries add nothing.
     */
    override suspend fun onPayment(payment: VerifiedPayment) {
        if (payment.source == PaymentSource.REFERRAL_BALANCE) return
        val attribution = repository.attributionOf(payment.userId) ?: return
        if (payments.firstProviderPaymentId(payment.userId) != payment.externalId) return
        val refundedSeen = maxOf(payment.refundedMinor, payments.refundedMinor(payment.externalId))
        val share = settledShareMinor(policy.sharePercent, payment.amountMinor, refundedSeen)
        if (share > 0) {
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
                    baseMinor = payment.amountMinor,
                    sharePercent = policy.sharePercent,
                    completed = false,
                    createdAt = now,
                )
            )
            if (added) logger.info("Referral: payment {} earned {} {} (minor) for the referrer, pending until {}",
                payment.externalId, share, payment.currency, now.plus(policy.hold))
        }
        // A refund recorded while this step ran found no accrual to settle; settle it now.
        val refundedNow = maxOf(payment.refundedMinor, payments.refundedMinor(payment.externalId))
        if (refundedNow > 0) settle(payment.externalId, "settle:${payment.externalId}:$refundedNow", full = false, refundedNow)
    }

    /**
     * A verified refund takes the share back down to the share of what the user kept: all of it for
     * a full refund. The reversal shares its accrual's availability date, so during the hold it
     * cancels the pending share; after the hold it lowers the available balance (which may go below
     * zero and then blocks spending).
     */
    override suspend fun onRefund(refund: VerifiedRefund) {
        settle(refund.paymentExternalId, refund.refundId, refund.full, refund.paymentRefundedMinor)
    }

    /**
     * Brings the accrual of [paymentExternalId] down to its target. Refunded = the larger of the
     * recorded refunds and what the provider reports ([providerRefundedMinor]), so a refund whose
     * own notification never arrived still counts. Both only ever grow; a refund recorded after
     * this read settles itself. The target uses the percent the accrual was earned at.
     */
    private suspend fun settle(paymentExternalId: String, key: String, full: Boolean, providerRefundedMinor: Long) {
        val refunded = maxOf(payments.refundedMinor(paymentExternalId), providerRefundedMinor)
        val reversal = repository.reverse(paymentExternalId, key, clock()) { accrual, alreadyReversed ->
            val base = accrual.baseMinor
            val percent = accrual.sharePercent
            val target = when {
                full -> 0
                base != null && percent != null -> settledShareMinor(percent, base, refunded)
                // Written before refunds settled against a base (never in production): keep it,
                // never guess; a full refund still takes everything back.
                else -> accrual.amountMinor - alreadyReversed
            }
            accrual.amountMinor - alreadyReversed - target
        }
        if (reversal != null) logger.info("Referral: refunds took back {} {} (minor) earned by payment {}",
            -reversal.amountMinor, reversal.currency, paymentExternalId)
    }
}
