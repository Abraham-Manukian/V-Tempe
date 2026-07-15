package com.vtempe.ui.screens

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import com.vtempe.shared.data.di.KoinProvider
import com.vtempe.shared.domain.repository.AuthRepository
import com.vtempe.shared.domain.repository.EntitlementRepository
import com.vtempe.ui.presenter.AuthPresenter
import com.vtempe.ui.presenter.AuthPresenterDelegate
import com.vtempe.ui.presenter.AuthUiState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.StateFlow

private class IosAuthPresenter(
    authRepository: AuthRepository,
    entitlementRepository: EntitlementRepository
) : AuthPresenter {
    private val job = SupervisorJob()
    private val scope = CoroutineScope(Dispatchers.Main + job)
    private val delegate = AuthPresenterDelegate(
        authRepository = authRepository,
        entitlementRepository = entitlementRepository,
        scope = scope
    )
    override val state: StateFlow<AuthUiState> get() = delegate.state
    override fun signIn(email: String, password: String) = delegate.signIn(email, password)
    override fun signUp(email: String, password: String) = delegate.signUp(email, password)
    override fun signOut() = delegate.signOut()
    override fun refresh() = delegate.refresh()
    fun close() = job.cancel()
}

@Composable
actual fun rememberAuthPresenter(): AuthPresenter {
    val presenter = remember {
        val koin = requireNotNull(KoinProvider.koin) { "Koin is not started" }
        IosAuthPresenter(
            authRepository = koin.get(),
            entitlementRepository = koin.get()
        )
    }
    DisposableEffect(Unit) { onDispose { presenter.close() } }
    return presenter
}
