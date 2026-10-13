package com.vtempe.ui.vm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vtempe.shared.domain.usecase.CheckSubscription
import com.vtempe.ui.presenter.PaywallPresenter
import com.vtempe.ui.presenter.PaywallPresenterDelegate
import com.vtempe.ui.presenter.PaywallState
import kotlinx.coroutines.flow.StateFlow

class PaywallViewModel(checkSubscription: CheckSubscription) : ViewModel(), PaywallPresenter {
    private val delegate = PaywallPresenterDelegate(checkSubscription, viewModelScope)
    override val state: StateFlow<PaywallState> = delegate.state
    override fun refresh() = delegate.refresh()
}
