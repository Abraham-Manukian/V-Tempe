package com.vtempe.server.features.account.data.service

import com.vtempe.server.features.entitlement.domain.port.EntitlementRepository
import com.vtempe.server.features.sync.domain.port.SyncBlobRepository

/**
 * Erases everything the server keeps for one user — Google Play's account-deletion requirement.
 * Idempotent: every step deletes "whatever is still there", so a retry after a partial failure
 * (or a repeated request) simply finishes the job.
 *
 * Deleted: synced progress (`sync_blobs`) and the current entitlement row (`entitlements`).
 *
 * Deliberately RETAINED: the `payments` ledger. Russian tax and accounting law requires payment
 * records to be kept for 5 years, so those rows stay untouched. They hold no profile or health
 * data, and the Firebase uid they carry no longer maps to an account once the client deletes the
 * Firebase user.
 *
 * Nothing else is keyed by user: AI requests carry no uid, and the bundle cache and raw LLM
 * telemetry are keyed by request id only.
 */
class AccountDeletionService(
    private val syncBlobs: SyncBlobRepository,
    private val entitlements: EntitlementRepository
) {
    suspend fun deleteAccountData(userId: String) {
        syncBlobs.deleteAll(userId)
        // revoke() deletes the user's entitlement row, which is exactly the erasure needed here.
        entitlements.revoke(userId)
    }
}
