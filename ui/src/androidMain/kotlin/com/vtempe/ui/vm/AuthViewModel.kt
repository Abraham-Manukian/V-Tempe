package com.vtempe.ui.vm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vtempe.shared.domain.repository.AuthErrorCode
import com.vtempe.shared.domain.repository.AuthRepository
import com.vtempe.shared.domain.repository.EntitlementRepository
import com.vtempe.shared.domain.repository.SyncRepository
import com.vtempe.ui.presenter.AuthPresenter
import com.vtempe.ui.presenter.AuthPresenterDelegate
import com.vtempe.ui.presenter.AuthUiState
import kotlinx.coroutines.flow.StateFlow

class AuthViewModel(
    authRepository: AuthRepository,
    entitlementRepository: EntitlementRepository,
    syncRepository: SyncRepository
) : ViewModel(), AuthPresenter {

    private val delegate = AuthPresenterDelegate(
        authRepository = authRepository,
        entitlementRepository = entitlementRepository,
        syncRepository = syncRepository,
        scope = viewModelScope
    )

    override val state: StateFlow<AuthUiState> get() = delegate.state
    override fun signIn(email: String, password: String) = delegate.signIn(email, password)
    override fun signUp(email: String, password: String) = delegate.signUp(email, password)
    override fun signInWithGoogle(idToken: String) = delegate.signInWithGoogle(idToken)
    override fun signInWithApple(idToken: String, rawNonce: String) = delegate.signInWithApple(idToken, rawNonce)
    override fun signOut() = delegate.signOut()
    override fun refresh() = delegate.refresh()
    override fun reportError(code: AuthErrorCode) = delegate.reportError(code)
}
