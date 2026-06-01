package com.vtempe.ui.presenter

import kotlinx.coroutines.flow.StateFlow

data class PaywallState(val active: Boolean = false)

interface PaywallPresenter {
    val state: StateFlow<PaywallState>
    fun refresh()
}
