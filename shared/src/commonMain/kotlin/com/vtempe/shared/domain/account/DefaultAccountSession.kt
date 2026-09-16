package com.vtempe.shared.domain.account

import com.vtempe.shared.domain.repository.AuthRepository
import io.github.aakira.napier.Napier
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class DefaultAccountSession(
    private val coordinator: AccountDataCoordinator,
    private val auth: AuthRepository,
    private val owner: LocalDataOwnerStore,
    private val scope: CoroutineScope
) : AccountSession {

    private val _state = MutableStateFlow<AccountDataState>(AccountDataState.Idle)
    override val state: StateFlow<AccountDataState> = _state.asStateFlow()

    private val _generation = MutableStateFlow(0)
    override val localDataGeneration: StateFlow<Int> = _generation.asStateFlow()

    private val mutex = Mutex()

    override fun onSignedIn(uid: String) = settle { coordinator.onSignedIn(uid).toState(uid) }

    override fun resolveGuestChoice(choice: GuestDataChoice) {
        val uid = (_state.value as? AccountDataState.NeedsGuestChoice)?.uid ?: return
        settle { coordinator.resolveGuestChoice(uid, choice).toState(uid) }
    }

    override fun retry() {
        val uid = (_state.value as? AccountDataState.Failed)?.uid ?: auth.authState.value?.uid ?: return
        onSignedIn(uid)
    }

    override fun signOut(force: Boolean) = settle {
        when (coordinator.signOut(force)) {
            SignOutOutcome.SignedOut -> AccountDataState.Idle
            SignOutOutcome.Unsynced -> AccountDataState.SignOutUnsynced
        }
    }

    override fun dismissSignOutWarning() {
        _state.compareAndSet(AccountDataState.SignOutUnsynced, AccountDataState.Idle)
    }

    override suspend fun reconcile() = mutex.withLock {
        val uid = auth.authState.value?.uid
        if (!owner.isTracking()) {
            owner.startTracking()
            // Installs from before owner tracking: data on a signed-in device is that account's.
            if (uid != null && owner.ownerUid() == null) {
                owner.setOwnerUid(uid)
                return@withLock
            }
        }
        val currentOwner = owner.ownerUid()
        val unsettled = (currentOwner != null && currentOwner != uid) ||
            (uid != null && (currentOwner == null || owner.isFullPushPending()))
        if (!unsettled) return@withLock

        // Choices and failures are only surfaced as state; the account screen shows them when opened.
        _state.value = runStep {
            if (uid == null) {
                coordinator.signOut(force = true)
                AccountDataState.Idle
            } else {
                coordinator.onSignedIn(uid).toState(uid)
            }
        } ?: AccountDataState.Idle
    }

    override suspend fun resetUserData() {
        // On the app scope: leaving the settings screen must not leave a half-wiped device.
        scope.launch {
            mutex.withLock {
                try {
                    coordinator.resetUserData()
                } finally {
                    _generation.value++
                }
            }
        }.join()
    }

    private fun settle(step: suspend () -> AccountDataState) {
        _state.value = AccountDataState.Syncing
        scope.launch {
            mutex.withLock {
                _state.value = runStep(step) ?: AccountDataState.Failed(auth.authState.value?.uid.orEmpty())
            }
        }
    }

    private suspend fun runStep(step: suspend () -> AccountDataState): AccountDataState? {
        val ownerBefore = owner.ownerUid()
        return try {
            step()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            Napier.e(tag = "Account", message = "account data transition failed", throwable = error)
            null
        } finally {
            if (owner.ownerUid() != ownerBefore) _generation.value++
        }
    }

    private fun AccountDataOutcome.toState(uid: String): AccountDataState = when (this) {
        AccountDataOutcome.Ready -> AccountDataState.Idle
        AccountDataOutcome.NeedsGuestChoice -> AccountDataState.NeedsGuestChoice(uid)
        AccountDataOutcome.Failed -> AccountDataState.Failed(uid)
    }
}
