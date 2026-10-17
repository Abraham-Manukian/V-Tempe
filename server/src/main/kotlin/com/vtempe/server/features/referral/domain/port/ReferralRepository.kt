package com.vtempe.server.features.referral.domain.port

import com.vtempe.server.features.referral.domain.model.Attribution
import com.vtempe.server.features.referral.domain.model.DebitResult
import com.vtempe.server.features.referral.domain.model.LedgerEntry
import java.time.Instant

/**
 * Referral codes, who invited whom, and the balance ledger. Every write is atomic on its own;
 * operations whose result depends on the balance ([reverse], [debit]) are serialized per user, on a
 * per-user lock that exists for every balance holder and survives account deletion, so two of them
 * can never both spend or take back the same money.
 */
interface ReferralRepository {
    suspend fun codeOf(userId: String): String?

    /** Stores [code] for [userId] unless the user already has one. Returns the user's code (the
     *  existing one wins), or null when [code] already belongs to someone else. */
    suspend fun saveCode(userId: String, code: String, now: Instant): String?

    suspend fun ownerOfCode(code: String): String?

    /** Records that [inviteeId] came through [referrerId]'s [code]. False when the invitee already
     *  has a referrer (the first attribution is final). */
    suspend fun attach(inviteeId: String, referrerId: String, code: String, now: Instant): Boolean

    suspend fun attributionOf(inviteeId: String): Attribution?

    suspend fun invitedCount(referrerId: String): Int

    /** Adds [entry] unless an entry with its sourceRef exists. True when it was added. */
    suspend fun appendIfAbsent(entry: LedgerEntry): Boolean

    /**
     * Takes back part of the accrual that [paymentExternalId] earned, once per [refundId] (any
     * idempotency key; the reversal's sourceRef is `reversal:<refundId>`).
     * [amountFor] gets the accrual and what earlier refunds already took back, and returns how
     * much this refund takes (it is capped at the remainder). Returns the reversal, or null when
     * the payment earned nothing, nothing remains, or this refund was already applied.
     */
    suspend fun reverse(
        paymentExternalId: String,
        refundId: String,
        now: Instant,
        amountFor: (accrual: LedgerEntry, alreadyReversedMinor: Long) -> Long,
    ): LedgerEntry?

    /** Debits [amountMinor] of [currency] for redemption [sourceRef] if the balance available at
     *  [now] covers it; a repeated [sourceRef] returns the original entry instead. */
    suspend fun debit(
        userId: String,
        sourceRef: String,
        currency: String,
        amountMinor: Long,
        periodDays: Int,
        now: Instant,
    ): DebitResult

    suspend fun entries(userId: String): List<LedgerEntry>

    suspend fun markCompleted(sourceRef: String)

    /** True once [userId]'s account was deleted (see [deleteUser]). */
    suspend fun isClosed(userId: String): Boolean

    /**
     * Account deletion: removes the user's code and every attribution they are part of, and closes
     * the account so its retained balance can never be spent ([debit] answers AccountClosed).
     * Ledger entries stay (money owed or paid is an accounting record).
     */
    suspend fun deleteUser(userId: String, now: Instant)
}
