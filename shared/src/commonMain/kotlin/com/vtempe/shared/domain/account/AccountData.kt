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

    /** The account's data could not be loaded. Nothing is synced until [AccountDataCoordinator.onSignedIn] succeeds. */
    data class Failed(val reason: AccountDataFailure) : AccountDataOutcome
}

/** Why the account's data could not be loaded: decides what the user is told (VT-AUD-076). */
enum class AccountDataFailure {
    /** The server no longer accepts this sign-in (401) or the account is no longer signed in. */
    SESSION_EXPIRED,

    /** The server refuses this account access (403). */
    ACCESS_DENIED,

    /** No connection, or the server did not answer in time. */
    OFFLINE,

    /** The server failed (5xx) or answered with something unexpected. */
    SERVER,

    /** The account's saved data cannot be read by this version of the app; none of it was written. */
    UNREADABLE_DATA,

    /** Something failed on the device itself. */
    UNEXPECTED,
}

enum class GuestDataChoice {
    KEEP_ACCOUNT_DATA,
    REPLACE_WITH_DEVICE_DATA
}

sealed interface AccountDeletionOutcome {
    /** Server data, the account itself and the device data are gone; the user is signed out. */
    data object Deleted : AccountDeletionOutcome

    /** The account's last sign-in is too old to delete it. Server data may already be erased; the
     *  device copy is uploaded again on the next settle unless the user re-authenticates and retries. */
    data object NeedsReauthentication : AccountDeletionOutcome

    /** Deletion did not complete (offline, server error). The account still exists and stays
     *  usable, with the same recovery as [NeedsReauthentication]; retrying is always safe. */
    data object Failed : AccountDeletionOutcome
}

sealed interface SignOutOutcome {
    data object SignedOut : SignOutOutcome

    /** Local data could not be uploaded; signing out now would delete it from the device. */
    data object Unsynced : SignOutOutcome
}
