package com.vtempe.server.shared.dto.referral

import kotlinx.serialization.Serializable

/** GET /me/referral. Amounts are in minor units of [ReferralBalanceDto.currency]. */
@Serializable
data class ReferralSummaryResponse(
    /** Null until the user asked for a code (POST /me/referral/code). */
    val code: String?,
    /** The user is subscribed, so their code invites people right now. */
    val canInvite: Boolean,
    /** The user entered someone's code. */
    val referred: Boolean,
    val invitedCount: Int,
    val rewardedCount: Int,
    val balances: List<ReferralBalanceDto>,
    val redeemOffers: List<RedeemOfferDto>,
)

@Serializable
data class ReferralBalanceDto(val currency: String, val availableMinor: Long, val pendingMinor: Long)

@Serializable
data class RedeemOfferDto(val currency: String, val priceMinor: Long, val periodDays: Int)

/** POST /me/referral/code. */
@Serializable
data class ReferralCodeResponse(val code: String)

/** POST /me/referral/attach. */
@Serializable
data class AttachReferralRequest(val code: String)

/** POST /me/referral/redeem. [requestId] is the client's idempotency key (8-64 of A-Z a-z 0-9 -). */
@Serializable
data class RedeemReferralRequest(val currency: String, val requestId: String)

@Serializable
data class RedeemReferralResponse(val expiresAt: String?)

/** Error body: a stable machine-readable [error] code the client maps to a message. */
@Serializable
data class ReferralErrorResponse(val error: String, val availableMinor: Long? = null, val priceMinor: Long? = null)
