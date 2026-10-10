package com.vtempe.ui.screens

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import com.vtempe.shared.data.di.KoinProvider
import com.vtempe.shared.data.repo.SleepStore
import com.vtempe.shared.domain.repository.AdviceRepository
import com.vtempe.shared.domain.usecase.RefreshSleepAdvice
import com.vtempe.ui.presenter.SleepPresenter
import com.vtempe.ui.presenter.SleepPresenterDelegate
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.datetime.LocalDate

private class IosSleepPresenter(
    refreshSleepAdvice: RefreshSleepAdvice,
    adviceRepository: AdviceRepository,
    sleepStore: SleepStore,
) : SleepPresenter {
    private val job = SupervisorJob()
    private val scope = CoroutineScope(Dispatchers.Main + job)
    private val delegate = SleepPresenterDelegate(
        refreshSleepAdvice = refreshSleepAdvice,
        adviceRepository = adviceRepository,
        sleepStore = sleepStore,
        scope = scope
    )
    override val state get() = delegate.state
    override fun refreshAdvice() = delegate.refreshAdvice()
    override fun logSleep(date: LocalDate, minutes: Int?, notes: String) = delegate.logSleep(date, minutes, notes)
    fun close() = job.cancel()
}

@Composable
actual fun rememberSleepPresenter(): SleepPresenter {
    val presenter = remember {
        val koin = requireNotNull(KoinProvider.koin) { "Koin is not started" }
        IosSleepPresenter(
            refreshSleepAdvice = koin.get(),
            adviceRepository = koin.get(),
            sleepStore = koin.get()
        )
    }
    DisposableEffect(Unit) { onDispose { presenter.close() } }
    return presenter
}
