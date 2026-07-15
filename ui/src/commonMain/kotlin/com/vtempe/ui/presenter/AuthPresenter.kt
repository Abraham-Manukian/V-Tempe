package com.vtempe.ui.presenter

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
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

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
    val entitlementExpiresAt: String? = null
)

interface AuthPresenter {
    val state: StateFlow<AuthUiState>
    fun signIn(email: String, password: String)
    fun signUp(email: String, password: String)
    fun signOut()
    /** Re-reads auth state (implicit via [AuthRepository.authState]) and, when signed in,
     *  re-fetches the entitlement. */
    fun refresh()
}

class AuthPresenterDelegate(
    private val authRepository: AuthRepository,
    private val entitlementRepository: EntitlementRepository,
    private val scope: CoroutineScope
) : AuthPresenter {

    private val _state = MutableStateFlow(AuthUiState())
    override val state: StateFlow<AuthUiState> = _state.asStateFlow()

    init {
        authRepository.authState.onEach { user ->
            _state.update { it.copy(user = user) }
            if (user != null) fetchEntitlement() else _state.update {
                it.copy(entitlementActive = null, entitlementExpiresAt = null)
            }
        }.launchIn(scope)
    }

    override fun refresh() {
        if (_state.value.user != null) fetchEntitlement()
    }

    override fun signIn(email: String, password: String) = authenticate { authRepository.signIn(email, password) }

    override fun signUp(email: String, password: String) = authenticate { authRepository.signUp(email, password) }

    override fun signOut() {
        scope.launch {
            runCatching { authRepository.signOut() }
                .onFailure { if (it is CancellationException) throw it }
        }
    }

    private fun authenticate(action: suspend () -> AuthUser) {
        _state.update { it.copy(loading = true, errorCode = null) }
        scope.launch {
            // authRepository.authState's listener updates `user` on success — no need to set it here.
            runCatching { action() }
                .onSuccess { _state.update { it.copy(loading = false) } }
                .onFailure { error ->
                    if (error is CancellationException) throw error
                    val code = (error as? AuthException)?.code ?: AuthErrorCode.UNKNOWN
                    _state.update { it.copy(loading = false, errorCode = code) }
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
