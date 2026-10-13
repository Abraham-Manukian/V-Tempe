package com.vtempe.server.features.account.data.service

import com.vtempe.server.features.entitlement.domain.port.EntitlementRepository
import com.vtempe.server.features.sync.domain.port.SyncBlobRepository
import java.time.Duration
import java.time.Instant

/**
 * Erases everything the server keeps for one user — Google Play's account-deletion requirement.
 * Idempotent: every step deletes "whatever is still there", so a retry after a partial failure
 * (or a repeated request) simply finishes the job.
 *
 * Deleted: synced progress (`sync_blobs`) and the current entitlement row (`entitlements`).
 * Added: a write barrier (`sync_write_barriers`, uid only) that refuses pushes of the deleted
 * user until every ID token issued before the deletion has expired.
 *
 * Deliberately RETAINED: the `payments` ledger, with its `payment_refunds` and the `grant_windows`
 * each payment bought (VT-AUD-052). Russian tax and accounting law requires payment
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
    private val clock: () -> Instant = Instant::now,
) {
    suspend fun deleteAccountData(userId: String) {
        // Another device may still hold a valid ID token and push its copy right after this
        // wipe (VT-AUD-059): its writes are refused until every token issued before now expired.
        val now = clock()
        syncBlobs.deleteAll(userId, now, blockWritesUntil = now.plus(SYNC_WRITE_BARRIER))
        // revoke() deletes the user's entitlement row, which is exactly the erasure needed here.
        entitlements.revoke(userId)
    }

    companion object {
        /** Firebase ID tokens live one hour; five more minutes cover clock skew. */
        val SYNC_WRITE_BARRIER: Duration = Duration.ofMinutes(65)
    }
}
