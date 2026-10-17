package com.vtempe.ui.vm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vtempe.shared.domain.referral.ReferralRepository
import com.vtempe.ui.presenter.ReferralPresenter
import com.vtempe.ui.presenter.ReferralPresenterDelegate
import com.vtempe.ui.presenter.ReferralUiState
import kotlinx.coroutines.flow.StateFlow

class ReferralViewModel(repository: ReferralRepository) : ViewModel(), ReferralPresenter {
    private val delegate = ReferralPresenterDelegate(repository, viewModelScope)
    override val state: StateFlow<ReferralUiState> = delegate.state
    override fun refresh() = delegate.refresh()
    override fun requestCode() = delegate.requestCode()
    override fun onCodeInput(value: String) = delegate.onCodeInput(value)
    override fun attach() = delegate.attach()
}
