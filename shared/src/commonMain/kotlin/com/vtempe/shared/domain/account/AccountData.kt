package com.vtempe.shared.domain.account

/**
 * Which account the user data stored on this device belongs to.
 *
 * `null` means the data is not bound to any account: either a guest's data or nothing at all.
 * Sync only ever uploads data whose owner is the signed-in account, so this is the single
 * guard that keeps one account's data out of another.
 */
interface LocalDataOwnerStore {
    fun ownerUid(): String?
    fun setOwnerUid(uid: String?)

    /** Set when device data was assigned to an account but the upload failed; retried on the next settle. */
    fun isFullPushPending(): Boolean
    fun setFullPushPending(pending: Boolean)

    /** False only on installs that predate owner tracking; see [AccountSession.reconcile]. */
    fun isTracking(): Boolean
    fun startTracking()
}

/** All per-user data kept on the device (profile, plans, progress, chat...). Device preferences are not included. */
interface LocalUserData {
    suspend fun hasUserData(): Boolean
    suspend fun clearUserData()
}

sealed interface AccountDataOutcome {
    /** Device data now belongs to the signed-in account. */
    data object Ready : AccountDataOutcome

    /** A guest with local data signed into an account that already has data; the user must pick one side. */
    data object NeedsGuestChoice : AccountDataOutcome

    /** The account's data could not be fetched. Nothing is synced until [AccountDataCoordinator.onSignedIn] succeeds. */
    data object Failed : AccountDataOutcome
}

enum class GuestDataChoice {
    KEEP_ACCOUNT_DATA,
    REPLACE_WITH_DEVICE_DATA
}

sealed interface SignOutOutcome {
    data object SignedOut : SignOutOutcome

    /** Local data could not be uploaded; signing out now would delete it from the device. */
    data object Unsynced : SignOutOutcome
}
