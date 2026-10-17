package com.vtempe.shared.data.network.dto

import kotlinx.serialization.Serializable

/** Wire shapes for /me/referral - field names must match the server's ReferralDtos
 *  (server/.../shared/dto/referral/ReferralDtos.kt). Defaults keep old clients decoding when the
 *  server adds fields. Amounts are minor units of their currency. */
@Serializable
data class ReferralSummaryDto(
    val code: String? = null,
    val canInvite: Boolean = false,
    val referred: Boolean = false,
    val invitedCount: Int = 0,
    val rewardedCount: Int = 0,
    val balances: List<ReferralBalanceDto> = emptyList(),
    val redeemOffers: List<RedeemOfferDto> = emptyList(),
    val sharePercent: Int = 0,
    val holdDays: Int = 0,
)

@Serializable
// No defaults on amounts: a renamed or missing field must fail decoding, never show a 0 balance.
data class ReferralBalanceDto(val currency: String, val availableMinor: Long, val pendingMinor: Long)

@Serializable
data class RedeemOfferDto(val currency: String, val priceMinor: Long, val periodDays: Int)

@Serializable
data class ReferralCodeDto(val code: String)

@Serializable
data class AttachReferralRequestDto(val code: String)

/** The server's error body: a stable machine-readable code. */
@Serializable
data class ReferralErrorDto(val error: String? = null)
