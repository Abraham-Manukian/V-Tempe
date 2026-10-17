package com.vtempe.server.features.referral.domain.model

import java.time.Duration
import java.time.Instant

/**
 * Owner's rules (2026-10-17): only a subscriber can invite; an invited user's FIRST verified
 * payment earns the referrer [sharePercent] of its price, held for [hold] (a refund in that time
 * cancels it); the balance pays for the referrer's own subscription ([offers]) now, and becomes
 * withdrawable once payouts exist.
 */
data class ReferralPolicy(
    val sharePercent: Int,
    val hold: Duration,
    val offers: List<RedeemOffer>,
) {
    init {
        require(sharePercent in 0..100) { "sharePercent must be 0..100 (0 turns rewards off)" }
        require(!hold.isNegative && hold <= MAX_HOLD) { "hold must be 0..$MAX_HOLD" }
    }

    fun offerFor(currency: String): RedeemOffer? = offers.firstOrNull { it.currency == currency }

    /** The referrer's share of [amountMinor], rounded down to a whole minor unit; never negative. */
    fun shareOf(amountMinor: Long): Long = Math.multiplyExact(amountMinor.coerceAtLeast(0), sharePercent.toLong()) / 100

    companion object {
        val MAX_HOLD: Duration = Duration.ofDays(365)
    }
}

/** One subscription period the balance can pay for, priced in [currency] minor units. */
data class RedeemOffer(val currency: String, val priceMinor: Long, val periodDays: Int) {
    init {
        require(CURRENCY.matches(currency)) { "currency must be an ISO 4217 code" }
        require(priceMinor > 0) { "price must be positive" }
        require(periodDays in 1..366) { "periodDays must be 1..366" }
    }

    private companion object {
        val CURRENCY = Regex("^[A-Z]{3}$")
    }
}

/** An invited user and who invited them. Set once, before the invited user's first payment. */
data class Attribution(val inviteeId: String, val referrerId: String, val code: String, val attachedAt: Instant)

enum class LedgerKind {
    /** The referrer's share of an invited user's first payment (positive, available after the hold). */
    ACCRUAL,
    /** A refund of that payment taking the share back (negative, same availability as its accrual). */
    REVERSAL,
    /** Balance spent on the referrer's own subscription (negative, immediately). */
    REDEMPTION,
}

/**
 * One movement of a user's referral balance. [sourceRef] is unique and makes every movement
 * idempotent: `accrual:<invitee>` (one per invited user, so only the first payment earns),
 * `reversal:<refund id>`, `redeem:<user>:<request id>`.
 */
data class LedgerEntry(
    val userId: String,
    val kind: LedgerKind,
    val sourceRef: String,
    val paymentExternalId: String?,
    val inviteeId: String?,
    val currency: String,
    val amountMinor: Long,
    val availableAt: Instant,
    /** REDEMPTION only: how many days of access it buys. */
    val periodDays: Int?,
    /** REDEMPTION only: the access it paid for has been granted. */
    val completed: Boolean,
    val createdAt: Instant,
)

data class ReferralBalance(val currency: String, val availableMinor: Long, val pendingMinor: Long)

data class ReferralSummary(
    val code: String?,
    val canInvite: Boolean,
    val referred: Boolean,
    val invitedCount: Int,
    val rewardedCount: Int,
    val balances: List<ReferralBalance>,
    val offers: List<RedeemOffer>,
)

enum class AttachResult {
    ATTACHED,
    INVALID_CODE,
    UNKNOWN_CODE,
    SELF_REFERRAL,
    /** The user already has a different referrer. */
    ALREADY_ATTACHED,
    /** The user has paid before: a referral counts only for new customers. */
    ALREADY_PAID,
    /** The code's owner has no active subscription, so the code does not invite anyone now. */
    CODE_INACTIVE,
    /** The caller's account was deleted (their ID token is still valid for a while). */
    ACCOUNT_CLOSED,
}

sealed interface IssueCodeResult {
    data class Issued(val code: String) : IssueCodeResult
    data object SubscriptionRequired : IssueCodeResult
}

sealed interface RedeemResult {
    data class Redeemed(val expiresAt: Instant?) : RedeemResult
    data class Insufficient(val availableMinor: Long, val priceMinor: Long) : RedeemResult
    data object NoOffer : RedeemResult
    data object InvalidRequest : RedeemResult
    /** The request id was already used for a different purchase. */
    data object RequestReused : RedeemResult
    data object AccountClosed : RedeemResult
}

/** Outcome of debiting the balance for a redemption. */
sealed interface DebitResult {
    data class Debited(val entry: LedgerEntry) : DebitResult
    /** The same request was debited before; returned so a retry can finish it. */
    data class AlreadyRecorded(val entry: LedgerEntry) : DebitResult
    data class Insufficient(val availableMinor: Long) : DebitResult
    /** The account was deleted: its retained balance cannot be spent. */
    data object AccountClosed : DebitResult
}

/**
 * Whether [entry] counts towards the balance that can be spent at [now]. A redemption always does,
 * whatever time it carries: comparing its timestamp with a caller's clock would let a second
 * request with an earlier clock skip it and spend the same money again.
 */
fun isAvailable(entry: LedgerEntry, now: Instant): Boolean =
    entry.kind == LedgerKind.REDEMPTION || !entry.availableAt.isAfter(now)

/** The spendable balance in [currency] at [now]: the single rule both repositories use for debits. */
fun availableMinor(entries: List<LedgerEntry>, currency: String, now: Instant): Long =
    entries.filter { it.currency == currency && isAvailable(it, now) }.sumOf { it.amountMinor }

/** How much a reversal may take: the [requestedMinor], capped at what the accrual still holds. */
fun reversalMinor(accrualMinor: Long, alreadyReversedMinor: Long, requestedMinor: Long): Long =
    requestedMinor.coerceIn(0, maxOf(accrualMinor - alreadyReversedMinor, 0))

/** Sums entries per currency: available = spendable at [now], pending = still on hold. */
fun balancesOf(entries: List<LedgerEntry>, now: Instant): List<ReferralBalance> =
    entries.groupBy { it.currency }.map { (currency, list) ->
        ReferralBalance(
            currency = currency,
            availableMinor = list.filter { isAvailable(it, now) }.sumOf { it.amountMinor },
            pendingMinor = list.filterNot { isAvailable(it, now) }.sumOf { it.amountMinor },
        )
    }.sortedBy { it.currency }
