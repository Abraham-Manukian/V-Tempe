package com.vtempe.server.features.entitlement.domain.model

import java.time.Duration
import java.time.Instant

/** The access window one payment bought: from [from] until [until]. */
data class GrantWindow(val from: Instant, val until: Instant)

/** A refund the provider confirmed (verified against its API, never taken from a webhook body). */
data class VerifiedRefund(
    val refundId: String,
    val paymentExternalId: String,
    val amountMinor: Long,
    val currency: String,
    /** The payment is now refunded in full (this refund alone or together with earlier ones). */
    val full: Boolean,
    val rawPayload: String,
    /** The provider's total refunded for the payment at verification time (all refunds so far),
     *  0 when unknown. Lets settlement count refunds whose own notification never arrived. */
    val paymentRefundedMinor: Long = 0,
)

/**
 * What a refund did to access (VT-AUD-052, owner's policy): a full refund removes only the
 * still-unused part of that payment's window, and later paid windows move up so none of them is
 * lost; anything else changes nothing and is kept for manual review.
 */
enum class RefundOutcome {
    ACCESS_REDUCED,
    /** Partial refund: access unchanged. */
    PARTIAL_REVIEW,
    /** The payment is not in the ledger, or was recorded before grant windows existed. */
    UNMATCHED_REVIEW,
    /** This refund was already processed. */
    DUPLICATE,
}

data class RefundResult(
    val outcome: RefundOutcome,
    val userId: String? = null,
    val removed: Duration = Duration.ZERO,
)
