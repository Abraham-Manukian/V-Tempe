package com.vtempe.server.features.account.data.service

import com.vtempe.server.features.entitlement.domain.port.EntitlementRepository
import com.vtempe.server.features.referral.domain.port.ReferralRepository
import com.vtempe.server.features.sync.domain.port.SyncBlobRepository
import java.time.Duration
import java.time.Instant

/**
 * Erases everything the server keeps for one user — Google Play's account-deletion requirement.
 * Idempotent: every step deletes "whatever is still there", so a retry after a partial failure
 * (or a repeated request) simply finishes the job.
 *
 * Deleted: synced progress (`sync_blobs`), the current entitlement row (`entitlements`), the
 * user's invite code and the invitations they are part of (`referral_codes`, `referral_attributions`).
 * Closed: the user's `referral_accounts` row, so the retained referral balance can never be spent.
 * Added: a write barrier (`sync_write_barriers`, uid only) that refuses pushes of the deleted
 * user until every ID token issued before the deletion has expired.
 *
 * Deliberately RETAINED: the `payments` ledger, with its `payment_refunds` and the `grant_windows`
 * each payment bought (VT-AUD-052), and the `referral_ledger` (referral money earned or spent). Russian tax and accounting law requires payment
 * records to be kept for 5 years, so those rows stay untouched. They hold no profile or health
 * data, and the Firebase uid they carry no longer maps to an account once the client deletes the
 * Firebase user.
 *
 * Nothing else is keyed by user: AI requests carry no uid, and the bundle cache and raw LLM
 * telemetry are keyed by request id only.
 */
class AccountDeletionService(
    private val syncBlobs: SyncBlobRepository,
    private val entitlements: EntitlementRepository,
    private val referrals: ReferralRepository,
    private val clock: () -> Instant = Instant::now,
) {
    suspend fun deleteAccountData(userId: String) {
        // Another device may still hold a valid ID token and push its copy right after this
        // wipe (VT-AUD-059): its writes are refused until every token issued before now expired.
        val now = clock()
        syncBlobs.deleteAll(userId, now, blockWritesUntil = now.plus(SYNC_WRITE_BARRIER))
        // Close the referral account BEFORE revoking access: a balance redemption racing this
        // deletion either sees the account closed, or grants before the revoke below removes it,
        // or (granting after it) re-checks and revokes itself (ReferralService.grantAccess).
        // The invite code and every invitation link go; balance movements stay, like payments.
        referrals.deleteUser(userId, now)
        // revoke() deletes the user's entitlement row, which is exactly the erasure needed here.
        entitlements.revoke(userId)
    }

    companion object {
        /** Firebase ID tokens live one hour; five more minutes cover clock skew. */
        val SYNC_WRITE_BARRIER: Duration = Duration.ofMinutes(65)
    }
}
