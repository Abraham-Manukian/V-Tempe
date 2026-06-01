package com.vtempe.ui.screens

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import com.vtempe.shared.data.di.KoinProvider
import com.vtempe.shared.domain.usecase.ValidateSubscription
import com.vtempe.ui.presenter.PaywallPresenter
import com.vtempe.ui.presenter.PaywallState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

private class IosPaywallPresenter(private val validateSubscription: ValidateSubscription) : PaywallPresenter {
    private val job = SupervisorJob()
    private val scope = CoroutineScope(Dispatchers.Main + job)
    private val mutableState = MutableStateFlow(PaywallState())
    override val state get() = mutableState
    override fun refresh() {
        scope.launch {
            val active = runCatching { validateSubscription() }.getOrDefault(false)
            mutableState.value = PaywallState(active)
        }
    }
    fun close() = job.cancel()
}

@Composable
actual fun rememberPaywallPresenter(): PaywallPresenter {
    val presenter = remember {
        val koin = requireNotNull(KoinProvider.koin) { "Koin is not started" }
        IosPaywallPresenter(validateSubscription = koin.get())
    }
    DisposableEffect(Unit) { onDispose { presenter.close() } }
    return presenter
}
