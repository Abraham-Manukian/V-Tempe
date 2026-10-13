package com.vtempe.ui.presenter

import com.vtempe.shared.domain.usecase.CheckSubscription
import com.vtempe.shared.domain.usecase.SubscriptionStatus
import io.github.aakira.napier.Napier
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** [status] null until the first check finishes. */
data class PaywallState(val status: SubscriptionStatus? = null, val loading: Boolean = false)

interface PaywallPresenter {
    val state: StateFlow<PaywallState>
    fun refresh()
}

/** Shared by Android and iOS: the status always comes from the server (VT-AUD-053). */
class PaywallPresenterDelegate(
    private val checkSubscription: CheckSubscription,
    private val scope: CoroutineScope,
) : PaywallPresenter {
    private val _state = MutableStateFlow(PaywallState())
    override val state: StateFlow<PaywallState> = _state.asStateFlow()

    override fun refresh() {
        if (_state.value.loading) return
        _state.update { it.copy(loading = true) }
        scope.launch {
            val status = try {
                checkSubscription()
            } catch (cancelled: CancellationException) {
                _state.update { it.copy(loading = false) }
                throw cancelled
            } catch (error: Exception) {
                Napier.w(tag = "Paywall", message = "subscription check failed: ${error::class.simpleName}")
                SubscriptionStatus.Unknown
            }
            _state.value = PaywallState(status = status, loading = false)
        }
    }
}
