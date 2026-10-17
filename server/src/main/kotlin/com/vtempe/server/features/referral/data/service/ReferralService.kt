package com.vtempe.server.features.referral.data.service

import com.vtempe.server.features.entitlement.data.service.EntitlementService
import com.vtempe.server.features.entitlement.domain.model.PaymentSource
import com.vtempe.server.features.referral.data.InviteCodes
import com.vtempe.server.features.referral.domain.model.AttachResult
import com.vtempe.server.features.referral.domain.model.DebitResult
import com.vtempe.server.features.referral.domain.model.IssueCodeResult
import com.vtempe.server.features.referral.domain.model.LedgerEntry
import com.vtempe.server.features.referral.domain.model.LedgerKind
import com.vtempe.server.features.referral.domain.model.RedeemResult
import com.vtempe.server.features.referral.domain.model.ReferralPolicy
import com.vtempe.server.features.referral.domain.model.ReferralSummary
import com.vtempe.server.features.referral.domain.model.balancesOf
import com.vtempe.server.features.referral.domain.port.ReferralRepository
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.time.Duration
import java.time.Instant

/** What a signed-in user can do with referrals: get a code, enter one, see and spend the balance. */
class ReferralService(
    private val repository: ReferralRepository,
    private val entitlements: EntitlementService,
    private val policy: ReferralPolicy,
    private val newCode: () -> String = InviteCodes::generate,
    private val clock: () -> Instant = Instant::now,
) {
    /** Read-only: nothing here writes (repairs happen in [redeem]). */
    suspend fun summary(userId: String): ReferralSummary {
        val entries = repository.entries(userId)
        return ReferralSummary(
            code = repository.codeOf(userId),
            canInvite = entitlements.isActiveOrGrace(userId),
            referred = repository.attributionOf(userId) != null,
            invitedCount = repository.invitedCount(userId),
            rewardedCount = rewardedInvitees(entries),
            balances = balancesOf(entries, clock()),
            offers = policy.offers,
            sharePercent = policy.sharePercent,
            holdDays = policy.hold.toDays().toInt(),
        )
    }

    /** Only a subscriber gets a code; asking again returns the same code. */
    suspend fun issueCode(userId: String): IssueCodeResult {
        if (repository.isClosed(userId)) return IssueCodeResult.SubscriptionRequired
        repository.codeOf(userId)?.let { existing ->
            return if (entitlements.isActiveOrGrace(userId)) IssueCodeResult.Issued(existing) else IssueCodeResult.SubscriptionRequired
        }
        if (!entitlements.isActiveOrGrace(userId)) return IssueCodeResult.SubscriptionRequired
        repeat(CODE_ATTEMPTS) {
            repository.saveCode(userId, newCode(), clock())?.let { return IssueCodeResult.Issued(it) }
        }
        error("could not allocate a unique referral code in $CODE_ATTEMPTS attempts")
    }

    /**
     * Binds the user to the code's owner. Allowed once, before the user's first payment, and only
     * while the owner is subscribed. Entering the same code again succeeds (safe client retries).
     */
    suspend fun attach(userId: String, rawCode: String): AttachResult {
        val code = InviteCodes.normalize(rawCode) ?: return AttachResult.INVALID_CODE
        if (repository.isClosed(userId)) return AttachResult.ACCOUNT_CLOSED
        val referrerId = repository.ownerOfCode(code) ?: return AttachResult.UNKNOWN_CODE
        if (referrerId == userId) return AttachResult.SELF_REFERRAL
        repository.attributionOf(userId)?.let { return sameReferrerOrConflict(it.referrerId, referrerId) }
        if (entitlements.hasPayments(userId)) return AttachResult.ALREADY_PAID
        if (!entitlements.isActiveOrGrace(referrerId) || repository.isClosed(referrerId)) return AttachResult.CODE_INACTIVE
        if (repository.attach(userId, referrerId, code, clock())) return AttachResult.ATTACHED
        // A concurrent attach won; report what is stored now.
        val stored = repository.attributionOf(userId) ?: return AttachResult.ALREADY_ATTACHED
        return sameReferrerOrConflict(stored.referrerId, referrerId)
    }

    /**
     * Spends the available balance on one period of the user's own subscription. [requestId] is
     * the client's idempotency key: a retry of the same request never pays twice, and it finishes
     * a redemption that was debited but whose access was not granted yet (e.g. a crash in between).
     */
    suspend fun redeem(userId: String, currency: String, requestId: String): RedeemResult {
        if (!REQUEST_ID.matches(requestId)) return RedeemResult.InvalidRequest
        val offer = policy.offerFor(currency.trim().uppercase()) ?: return RedeemResult.NoOffer
        // Finish any earlier redemption that was paid but not granted (a crash between the steps).
        completePendingRedemptions(userId)
        val sourceRef = "redeem:$userId:$requestId"
        val entry = when (val debit = repository.debit(userId, sourceRef, offer.currency, offer.priceMinor, offer.periodDays, clock())) {
            is DebitResult.Insufficient -> return RedeemResult.Insufficient(debit.availableMinor, offer.priceMinor)
            DebitResult.AccountClosed -> return RedeemResult.AccountClosed
            is DebitResult.Debited -> debit.entry
            is DebitResult.AlreadyRecorded ->
                if (debit.entry.currency != offer.currency) return RedeemResult.RequestReused else debit.entry
        }
        grantAccess(entry)
        return RedeemResult.Redeemed(entitlements.current(userId).expiresAt)
    }

    /** Finishes debited redemptions whose access grant did not happen (crash, outage). Never for a
     *  deleted account: its access must not come back. */
    private suspend fun completePendingRedemptions(userId: String) {
        if (repository.isClosed(userId)) return
        repository.entries(userId)
            .filter { it.kind == LedgerKind.REDEMPTION && !it.completed }
            .forEach { grantAccess(it) }
    }

    /** Idempotent on the redemption's sourceRef, which is also its payment id in the payments ledger. */
    private suspend fun grantAccess(redemption: LedgerEntry) {
        if (!redemption.completed) {
            entitlements.recordPaymentAndExtend(
                externalId = redemption.sourceRef,
                userId = redemption.userId,
                source = PaymentSource.REFERRAL_BALANCE,
                amountMinor = -redemption.amountMinor,
                currency = redemption.currency,
                rawPayload = buildJsonObject {
                    put("kind", "referral_redemption")
                    put("sourceRef", redemption.sourceRef)
                }.toString(),
                period = Duration.ofDays((redemption.periodDays ?: DEFAULT_PERIOD_DAYS).toLong()),
            )
            repository.markCompleted(redemption.sourceRef)
            // The account may have been deleted between the debit and the grant: the deletion has
            // revoked access already, so take back what was just granted.
            if (repository.isClosed(redemption.userId)) entitlements.revoke(redemption.userId)
        }
    }

    private fun sameReferrerOrConflict(stored: String, requested: String) =
        if (stored == requested) AttachResult.ATTACHED else AttachResult.ALREADY_ATTACHED

    /** Invited users whose share has not been fully taken back by refunds. */
    private fun rewardedInvitees(entries: List<LedgerEntry>): Int =
        entries.filter { it.kind != LedgerKind.REDEMPTION && it.inviteeId != null }
            .groupBy { it.inviteeId }
            .count { (_, list) -> list.sumOf { it.amountMinor } > 0 }

    private companion object {
        const val CODE_ATTEMPTS = 5
        const val DEFAULT_PERIOD_DAYS = 30
        val REQUEST_ID = Regex("^[A-Za-z0-9-]{8,64}$")
    }
}
