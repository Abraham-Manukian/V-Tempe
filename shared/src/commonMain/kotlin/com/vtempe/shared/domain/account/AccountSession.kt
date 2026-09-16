package com.vtempe.shared.domain.account

import kotlinx.coroutines.flow.StateFlow

sealed interface AccountDataState {
    data object Idle : AccountDataState
    data object Syncing : AccountDataState
    data class NeedsGuestChoice(val uid: String) : AccountDataState
    data class Failed(val uid: String) : AccountDataState
    data object SignOutUnsynced : AccountDataState
}

/**
 * App-lifetime owner of account data transitions. Work runs outside any screen scope, so leaving
 * the screen that started a sign-in can't abandon a half-finished switch.
 */
interface AccountSession {
    val state: StateFlow<AccountDataState>

    /** Incremented whenever device user data was cleared or replaced; screens holding it must rebuild. */
    val localDataGeneration: StateFlow<Int>

    fun onSignedIn(uid: String)
    fun resolveGuestChoice(choice: GuestDataChoice)
    fun retry()
    fun signOut(force: Boolean = false)
    fun dismissSignOutWarning()

    /** At launch: settles data left inconsistent by an interrupted switch. Never shows choices by itself. */
    suspend fun reconcile()

    /** Deletes all user data (see [AccountDataCoordinator.resetUserData]). Completes even if the caller is cancelled. */
    suspend fun resetUserData()
}
