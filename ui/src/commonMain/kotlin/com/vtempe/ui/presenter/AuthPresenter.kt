package com.vtempe.ui.presenter

import com.vtempe.shared.domain.account.AccountDataState
import com.vtempe.shared.domain.account.AccountSession
import com.vtempe.shared.domain.account.GuestDataChoice
import com.vtempe.shared.domain.repository.AuthErrorCode
import com.vtempe.shared.domain.repository.AuthException
import com.vtempe.shared.domain.repository.AuthRepository
import com.vtempe.shared.domain.repository.AuthUser
import com.vtempe.shared.domain.repository.EntitlementRepository
import com.vtempe.shared.domain.util.DataResult
import io.github.aakira.napier.Napier
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

internal const val AUTH_REQUEST_TIMEOUT_MS = 20_000L
internal const val CREDENTIAL_PICKER_TIMEOUT_MS = 60_000L

data class AuthUiState(
    val user: AuthUser? = null,
    val loading: Boolean = false,
    /** Machine-readable — [AuthScreen] maps this to a localized string. Never a raw message,
     *  this is an RU-first app. */
    val errorCode: AuthErrorCode? = null,
    /** null = not loaded yet, or the fetch failed (e.g. no database provisioned server-side
     *  yet) — not shown as an error, just an unresolved state, since S2's payment
     *  infrastructure isn't fully live yet. */
    val entitlementActive: Boolean? = null,
    val entitlementExpiresAt: String? = null,
    /** Where the device data stands relative to the signed-in account (choice, failure, unsynced sign-out). */
    val accountData: AccountDataState = AccountDataState.Idle,
    /** Bumps whenever device data was cleared or replaced; the UI rebuilds from scratch. */
    val localDataGeneration: Int = 0
)

interface AuthPresenter {
    val state: StateFlow<AuthUiState>
    fun signIn(email: String, password: String)
    fun signUp(email: String, password: String)
    fun signInWithGoogle(idToken: String)
    fun signInWithApple(idToken: String, rawNonce: String)
    fun signOut()
    /** Signs out even though local data couldn't be uploaded — it is deleted from the device. */
    fun confirmSignOut()
    fun dismissSignOutWarning()
    fun resolveGuestChoice(choice: GuestDataChoice)
    fun retryAccountData()
    /** Re-reads auth state (implicit via [AuthRepository.authState]) and, when signed in,
     *  re-fetches the entitlement. */
    fun refresh()
    /** For platform-side failures that never reach [AuthRepository] — e.g. the user's Google/
     *  Apple credential picker itself failed or has nothing to offer. Cancellation isn't an
     *  error and should NOT call this. */
    fun reportError(code: AuthErrorCode)
}

class AuthPresenterDelegate(
    private val authRepository: AuthRepository,
    private val entitlementRepository: EntitlementRepository,
    private val accountSession: AccountSession,
    private val scope: CoroutineScope
) : AuthPresenter {

    private val authLoading = MutableStateFlow(false)
    // Seeded from the session so a screen created after a switch doesn't mistake the current
    // generation for a new change.
    private val _state = MutableStateFlow(
        AuthUiState(
            accountData = accountSession.state.value,
            localDataGeneration = accountSession.localDataGeneration.value
        )
    )
    override val state: StateFlow<AuthUiState> = _state.asStateFlow()

    init {
        authRepository.authState.onEach { user ->
            _state.update { it.copy(user = user) }
            if (user != null) fetchEntitlement() else _state.update {
                it.copy(entitlementActive = null, entitlementExpiresAt = null)
            }
        }.launchIn(scope)
        // Signing in isn't finished until the device data belongs to the new account.
        combine(authLoading, accountSession.state, accountSession.localDataGeneration) { loading, data, generation ->
            _state.update {
                it.copy(
                    loading = loading || data == AccountDataState.Syncing,
                    accountData = data,
                    localDataGeneration = generation
                )
            }
        }.launchIn(scope)
    }

    override fun refresh() {
        if (_state.value.user != null) fetchEntitlement()
    }

    override fun signIn(email: String, password: String) = authenticate { authRepository.signIn(email, password) }

    override fun signUp(email: String, password: String) = authenticate { authRepository.signUp(email, password) }

    override fun signInWithGoogle(idToken: String) = authenticate { authRepository.signInWithGoogle(idToken) }

    override fun signInWithApple(idToken: String, rawNonce: String) =
        authenticate { authRepository.signInWithApple(idToken, rawNonce) }

    override fun reportError(code: AuthErrorCode) {
        if (!_state.value.loading) _state.update { it.copy(errorCode = code) }
    }

    override fun signOut() = accountSession.signOut(force = false)

    override fun confirmSignOut() = accountSession.signOut(force = true)

    override fun dismissSignOutWarning() = accountSession.dismissSignOutWarning()

    override fun resolveGuestChoice(choice: GuestDataChoice) = accountSession.resolveGuestChoice(choice)

    override fun retryAccountData() = accountSession.retry()

    private fun authenticate(action: suspend () -> AuthUser) {
        if (_state.value.loading) return
        authLoading.value = true
        _state.update { it.copy(loading = true, errorCode = null) }
        scope.launch {
            try {
                // Firebase may keep waiting while offline. Bound the UI wait, without treating
                // cancellation of the owning screen as a network error.
                val user = withTimeoutOrNull(AUTH_REQUEST_TIMEOUT_MS) { action() }
                if (user == null) {
                    _state.update { it.copy(errorCode = AuthErrorCode.NETWORK) }
                } else {
                    // Runs on the app scope: leaving this screen must not abandon the data switch.
                    accountSession.onSignedIn(user.uid)
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                val code = (error as? AuthException)?.code ?: AuthErrorCode.UNKNOWN
                _state.update { it.copy(errorCode = code) }
            } finally {
                authLoading.value = false
                _state.update { it.copy(loading = accountSession.state.value == AccountDataState.Syncing) }
            }
        }
    }

    private fun fetchEntitlement() {
        scope.launch {
            when (val result = entitlementRepository.fetchEntitlement()) {
                is DataResult.Success -> _state.update {
                    it.copy(entitlementActive = result.data.active, entitlementExpiresAt = result.data.expiresAt)
                }
                is DataResult.Failure -> {
                    Napier.d(tag = "Auth", message = "entitlement fetch failed: ${result.reason}")
                    _state.update { it.copy(entitlementActive = null, entitlementExpiresAt = null) }
                }
            }
        }
    }
}
