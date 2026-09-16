package com.vtempe.shared.domain.account

import com.vtempe.shared.domain.repository.AuthRepository
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
    private val sync: SyncRepository
) {
    suspend fun onSignedIn(uid: String): AccountDataOutcome {
        val currentOwner = owner.ownerUid()
        if (currentOwner == uid) {
            if (owner.isFullPushPending()) uploadDeviceData(uid)
            return AccountDataOutcome.Ready
        }
        if (currentOwner != null) {
            // Another account's data: remove it before anything else, even if the fetch below fails.
            localData.clearUserData()
            owner.setOwnerUid(null)
            owner.setFullPushPending(false)
        }

        val remote = sync.fetchRemote(uid) ?: return AccountDataOutcome.Failed
        if (!localData.hasUserData()) {
            sync.restore(remote)
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

    suspend fun resolveGuestChoice(uid: String, choice: GuestDataChoice): AccountDataOutcome =
        when (choice) {
            GuestDataChoice.KEEP_ACCOUNT_DATA -> {
                val remote = sync.fetchRemote(uid) ?: return AccountDataOutcome.Failed
                localData.clearUserData()
                sync.restore(remote)
                owner.setOwnerUid(uid)
                AccountDataOutcome.Ready
            }
            GuestDataChoice.REPLACE_WITH_DEVICE_DATA -> {
                owner.setOwnerUid(uid)
                uploadDeviceData(uid)
                AccountDataOutcome.Ready
            }
        }

    /**
     * Signs out and removes the account's data from the device. Without [force], refuses when the
     * data cannot be uploaded first, so the caller can ask the user. Guest data (no owner) is kept.
     */
    suspend fun signOut(force: Boolean = false): SignOutOutcome {
        val uid = auth.authState.value?.uid
        val currentOwner = owner.ownerUid()
        if (!force && uid != null && currentOwner == uid && !sync.pushAll(uid)) {
            return SignOutOutcome.Unsynced
        }
        auth.signOut()
        if (currentOwner != null) {
            localData.clearUserData()
            owner.setOwnerUid(null)
            owner.setFullPushPending(false)
        }
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

    private suspend fun uploadDeviceData(uid: String) {
        owner.setFullPushPending(!sync.pushAll(uid))
    }
}
