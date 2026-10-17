package com.vtempe.shared.domain.referral

import com.vtempe.shared.domain.util.DataResult

/** One currency of the referral balance, in minor units (kopecks, cents). */
data class ReferralMoney(val currency: String, val availableMinor: Long, val pendingMinor: Long)

/** What the invite screen shows. The server decides everything here (rules, amounts). */
data class ReferralOverview(
    /** Null until the user asks for a code. */
    val code: String?,
    /** The user is subscribed, so their code invites people right now. */
    val canInvite: Boolean,
    /** The user already entered someone's code. */
    val referred: Boolean,
    val invitedCount: Int,
    val rewardedCount: Int,
    val balances: List<ReferralMoney>,
    /** The balance can be spent on the subscription (off until the owner sets a price). */
    val spendingAvailable: Boolean,
    /** Server rules: the inviter's share of the first payment, and how many days it is held. */
    val sharePercent: Int,
    val holdDays: Int,
)

sealed interface ReferralCodeResult {
    data class Issued(val code: String) : ReferralCodeResult
    data object SubscriptionRequired : ReferralCodeResult
    data class Failed(val failure: DataResult.Failure) : ReferralCodeResult
}

enum class AttachCodeResult {
    ATTACHED,
    INVALID_CODE,
    UNKNOWN_CODE,
    SELF_REFERRAL,
    ALREADY_REFERRED,
    ALREADY_PAID,
    CODE_INACTIVE,
    ACCOUNT_DELETED,
    SIGN_IN_REQUIRED,
    /** Network, timeout or an unexpected server answer: worth retrying. */
    FAILED,
}

interface ReferralRepository {
    /** HTTP 401 (signed out) comes back as a Failure with code 401. */
    suspend fun overview(): DataResult<ReferralOverview>
    suspend fun requestCode(): ReferralCodeResult
    suspend fun attach(code: String): AttachCodeResult
}
