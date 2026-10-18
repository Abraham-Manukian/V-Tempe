package com.vtempe.shared.domain.account

import com.vtempe.shared.domain.consent.HealthDataConsentManager
import com.vtempe.shared.domain.repository.AuthErrorCode
import com.vtempe.shared.domain.repository.AuthException
import com.vtempe.shared.domain.repository.AuthRepository
import com.vtempe.shared.domain.repository.RemoteAccountRepository
import com.vtempe.shared.domain.repository.RemoteFetch
import com.vtempe.shared.domain.repository.SyncRepository

/**
 * Keeps device data consistent with the signed-in account.
 *
 * Invariant: local data is uploaded only while [LocalDataOwnerStore.ownerUid] equals the
 * signed-in uid, and the owner is set only after the device holds exactly that account's data
 * (or data the user explicitly moved into it). Callers must not run these methods concurrently.
 */
class AccountDataCoordinator(
    private val auth: AuthRepository,
    private val owner: LocalDataOwnerStore,
    private val localData: LocalUserData,
    private val sync: SyncRepository,
    private val remoteAccount: RemoteAccountRepository,
    private val healthConsent: HealthDataConsentManager,
) {
    suspend fun onSignedIn(uid: String): AccountDataOutcome {
        val currentOwner = owner.ownerUid()
        if (currentOwner == uid) {
            if (owner.isFullPushPending()) uploadDeviceData(uid) else sync.pushPending()
            return AccountDataOutcome.Ready
        }
        if (currentOwner != null) {
            // Another account's data: remove it before anything else, even if the fetch below fails.
            clearDeviceData()
        }

        val remote = when (val fetch = sync.fetchRemote(uid)) {
            is RemoteFetch.Fetched -> fetch.snapshot
            is RemoteFetch.Failed -> return AccountDataOutcome.Failed(fetch.reason)
        }
        if (!localData.hasUserData()) {
            // Ready only once the account's data is actually on the device (VT-AUD-060): an
            // unreadable snapshot fails and is retried, it never becomes an empty account.
            if (sync.restore(remote).isNotEmpty()) return unreadable
            owner.setOwnerUid(uid)
            return AccountDataOutcome.Ready
        }
        if (remote.isEmpty) {
            owner.setOwnerUid(uid)
            uploadDeviceData(uid)
            return AccountDataOutcome.Ready
        }
        return AccountDataOutcome.NeedsGuestChoice
    }

    /** True when the device data has parts the account's server copy lacks (an upload failed, or
     *  the health-data consent held it back): a sign-out the user did not ask for must keep it. */
    fun holdsUnbackedData(): Boolean = owner.ownerUid() != null && !owner.isWipeRequested() &&
        (owner.isFullPushPending() || sync.hasUndeliveredChanges())

    /** Delivers changes a failed or interrupted upload left behind, when [uid] owns the device data. */
    suspend fun deliverPending(uid: String) {
        if (owner.ownerUid() == uid) sync.pushPending()
    }

    suspend fun resolveGuestChoice(uid: String, choice: GuestDataChoice): AccountDataOutcome =
        when (choice) {
            GuestDataChoice.KEEP_ACCOUNT_DATA -> keepAccountData(uid)
            GuestDataChoice.REPLACE_WITH_DEVICE_DATA -> {
                owner.setOwnerUid(uid)
                uploadDeviceData(uid)
                AccountDataOutcome.Ready
            }
        }

    private suspend fun keepAccountData(uid: String): AccountDataOutcome {
        val remote = when (val fetch = sync.fetchRemote(uid)) {
            is RemoteFetch.Fetched -> fetch.snapshot
            is RemoteFetch.Failed -> return AccountDataOutcome.Failed(fetch.reason)
        }
        // Checked before the device copy is cleared, so a corrupt account never costs it.
        if (sync.unreadableDomains(remote).isNotEmpty()) return unreadable
        localData.clearUserData()
        if (sync.restore(remote).isNotEmpty()) return unreadable
        owner.setOwnerUid(uid)
        return AccountDataOutcome.Ready
    }

    /**
     * Signs out and removes the account's data from the device. Without [force], refuses when the
     * data cannot be uploaded first, so the caller can ask the user. Guest data (no owner) is kept.
     */
    suspend fun signOut(force: Boolean = false): SignOutOutcome {
        val uid = auth.authState.value?.uid
        val currentOwner = owner.ownerUid()
        // Merged, not replaced: replacing would erase entries another device of the account added.
        if (!force && uid != null && currentOwner == uid && !sync.deliverAll(uid)) {
            return SignOutOutcome.Unsynced
        }
        // Remembered until done: a wipe the user asked for is finished at the next launch if the
        // app dies halfway, not kept like data of a session that ended by itself.
        if (currentOwner != null) owner.setWipeRequested(true)
        auth.signOut()
        // The decision on health data was this person's, whether or not the data was theirs.
        if (uid != null) healthConsent.forget()
        if (currentOwner != null) clearDeviceData()
        return SignOutOutcome.SignedOut
    }

    /**
     * "Start over": wipes every piece of user data on the device and, when that data belongs to the
     * signed-in account, the account's synced progress too, so nothing returns on the next sign-in.
     * The session stays signed in. If the account can't be reached, the wipe is finished on the
     * next [onSignedIn].
     */
    suspend fun resetUserData() {
        localData.clearUserData()
        val uid = auth.authState.value?.uid ?: return
        if (owner.ownerUid() == uid) uploadDeviceData(uid)
    }

    /**
     * Deletes the signed-in account: its server data first (that call needs the account's token),
     * then the account itself, then every piece of user data on the device. Stops at the first
     * failure, so the account is never gone while its server data survives. Retrying is safe.
     */
    suspend fun deleteAccount(): AccountDeletionOutcome {
        val uid = auth.authState.value?.uid ?: return AccountDeletionOutcome.Failed
        val ownsDeviceData = owner.ownerUid() == uid
        // Detach first, then let an upload already under way finish: one landing after the server
        // wipe would bring the data back (VT-AUD-059). Uploads starting later see no owner.
        if (ownsDeviceData) owner.setOwnerUid(null)
        sync.awaitIdle()
        var outcome: AccountDeletionOutcome = AccountDeletionOutcome.Failed
        try {
            outcome = deleteServerDataThenAccount(uid)
        } finally {
            if (outcome != AccountDeletionOutcome.Deleted && ownsDeviceData) {
                // The account lives on, but its server copy may already be erased: upload the
                // device's copy again on the next settle (a successful retry cancels this).
                owner.setOwnerUid(uid)
                owner.setFullPushPending(true)
            }
        }
        if (outcome == AccountDeletionOutcome.Deleted) clearDeviceData()
        return outcome
    }

    private suspend fun deleteServerDataThenAccount(uid: String): AccountDeletionOutcome {
        // Asked first: the server step revokes paid access and closes referrals, so a deletion
        // that would then stop at re-authentication must not get that far.
        val recent = try {
            auth.isSignInRecent()
        } catch (error: AuthException) {
            return AccountDeletionOutcome.Failed
        }
        if (!recent) return AccountDeletionOutcome.NeedsReauthentication
        if (!remoteAccount.deleteAccountData(uid)) return AccountDeletionOutcome.Failed
        return try {
            auth.deleteCurrentUser()
            AccountDeletionOutcome.Deleted
        } catch (error: AuthException) {
            if (error.code == AuthErrorCode.REQUIRES_RECENT_LOGIN) AccountDeletionOutcome.NeedsReauthentication
            else AccountDeletionOutcome.Failed
        }
    }

    private val unreadable = AccountDataOutcome.Failed(AccountDataFailure.UNREADABLE_DATA)

    private suspend fun clearDeviceData() {
        localData.clearUserData()
        owner.setOwnerUid(null)
        owner.setFullPushPending(false)
        // The data's person is gone from the device; so is their decision on health data.
        healthConsent.forget()
        owner.setWipeRequested(false)
    }

    private suspend fun uploadDeviceData(uid: String) {
        owner.setFullPushPending(!sync.pushAll(uid))
    }
}
