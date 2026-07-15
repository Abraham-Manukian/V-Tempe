package com.vtempe.ui.vm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vtempe.shared.domain.repository.AuthRepository
import com.vtempe.shared.domain.repository.EntitlementRepository
import com.vtempe.ui.presenter.AuthPresenter
import com.vtempe.ui.presenter.AuthPresenterDelegate
import com.vtempe.ui.presenter.AuthUiState
import kotlinx.coroutines.flow.StateFlow

class AuthViewModel(
    authRepository: AuthRepository,
    entitlementRepository: EntitlementRepository
) : ViewModel(), AuthPresenter {

    private val delegate = AuthPresenterDelegate(
        authRepository = authRepository,
        entitlementRepository = entitlementRepository,
        scope = viewModelScope
    )

    override val state: StateFlow<AuthUiState> get() = delegate.state
    override fun signIn(email: String, password: String) = delegate.signIn(email, password)
    override fun signUp(email: String, password: String) = delegate.signUp(email, password)
    override fun signOut() = delegate.signOut()
    override fun refresh() = delegate.refresh()
}
