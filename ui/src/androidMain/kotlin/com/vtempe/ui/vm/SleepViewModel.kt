package com.vtempe.ui.vm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vtempe.shared.data.repo.SleepStore
import com.vtempe.shared.domain.repository.AdviceRepository
import com.vtempe.shared.domain.usecase.RefreshSleepAdvice
import com.vtempe.ui.presenter.SleepPresenter
import com.vtempe.ui.presenter.SleepPresenterDelegate
import com.vtempe.ui.presenter.SleepState
import kotlinx.coroutines.flow.StateFlow
import kotlinx.datetime.LocalDate

class SleepViewModel(
    refreshSleepAdvice: RefreshSleepAdvice,
    adviceRepository: AdviceRepository,
    sleepStore: SleepStore,
) : ViewModel(), SleepPresenter {

    private val delegate = SleepPresenterDelegate(
        refreshSleepAdvice = refreshSleepAdvice,
        adviceRepository = adviceRepository,
        sleepStore = sleepStore,
        scope = viewModelScope
    )

    override val state: StateFlow<SleepState> = delegate.state

    override fun refreshAdvice() = delegate.refreshAdvice()
    override fun logSleep(date: LocalDate, minutes: Int?, notes: String) = delegate.logSleep(date, minutes, notes)
}
