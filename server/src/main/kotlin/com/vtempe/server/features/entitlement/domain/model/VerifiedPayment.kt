package com.vtempe.server.features.entitlement.domain.model

/**
 * A payment its provider confirmed: re-fetched from the provider's API (YooKassa) or verified
 * with the store (Google Play / App Store), never taken from a notification body.
 * [refundedMinor] is how much of it the provider reports as refunded at verification time.
 */
data class VerifiedPayment(
    val externalId: String,
    val userId: String,
    val source: PaymentSource,
    val amountMinor: Long,
    val refundedMinor: Long,
    val currency: String,
)
