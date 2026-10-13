package com.vtempe.ui.screens

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import com.vtempe.shared.data.di.KoinProvider
import com.vtempe.shared.domain.usecase.CheckSubscription
import com.vtempe.ui.presenter.PaywallPresenter
import com.vtempe.ui.presenter.PaywallPresenterDelegate
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

private class IosPaywallPresenter(checkSubscription: CheckSubscription) : PaywallPresenter {
    private val job = SupervisorJob()
    private val delegate = PaywallPresenterDelegate(checkSubscription, CoroutineScope(Dispatchers.Main + job))
    override val state get() = delegate.state
    override fun refresh() = delegate.refresh()
    fun close() = job.cancel()
}

@Composable
actual fun rememberPaywallPresenter(): PaywallPresenter {
    val presenter = remember {
        val koin = requireNotNull(KoinProvider.koin) { "Koin is not started" }
        IosPaywallPresenter(checkSubscription = koin.get())
    }
    DisposableEffect(Unit) { onDispose { presenter.close() } }
    return presenter
}
