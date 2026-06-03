package com.vtempe.ui.vm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vtempe.shared.data.repo.SleepStore
import com.vtempe.shared.domain.repository.AdviceRepository
import com.vtempe.shared.domain.repository.ProfileRepository
import com.vtempe.ui.presenter.SleepPresenter
import com.vtempe.ui.presenter.SleepPresenterDelegate
import com.vtempe.ui.presenter.SleepState
import kotlinx.coroutines.flow.StateFlow

class SleepViewModel(
    adviceRepository: AdviceRepository,
    profileRepository: ProfileRepository,
    sleepStore: SleepStore,
) : ViewModel(), SleepPresenter {

    private val delegate = SleepPresenterDelegate(
        adviceRepository = adviceRepository,
        profileRepository = profileRepository,
        sleepStore = sleepStore,
        scope = viewModelScope
    )

    override val state: StateFlow<SleepState> = delegate.state

    override fun sync() = delegate.sync()
    override fun logSleep(hours: Int, minutes: Int) = delegate.logSleep(hours, minutes)
}
