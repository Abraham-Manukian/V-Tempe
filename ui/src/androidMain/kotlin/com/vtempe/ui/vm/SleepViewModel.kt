package com.vtempe.ui.vm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vtempe.shared.domain.repository.AdviceRepository
import com.vtempe.shared.domain.repository.ProfileRepository
import com.vtempe.ui.screens.SleepPresenter
import com.vtempe.ui.screens.SleepPresenterDelegate
import com.vtempe.ui.screens.SleepState
import kotlinx.coroutines.flow.StateFlow

class SleepViewModel(
    adviceRepository: AdviceRepository,
    profileRepository: ProfileRepository,
) : ViewModel(), SleepPresenter {

    private val delegate = SleepPresenterDelegate(
        adviceRepository = adviceRepository,
        profileRepository = profileRepository,
        scope = viewModelScope
    )

    override val state: StateFlow<SleepState> = delegate.state

    override fun sync() = delegate.sync()
}
