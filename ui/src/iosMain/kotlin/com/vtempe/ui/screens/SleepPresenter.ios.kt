package com.vtempe.ui.screens

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import com.vtempe.shared.data.di.KoinProvider
import com.vtempe.shared.data.repo.SleepStore
import com.vtempe.shared.domain.repository.AiTrainerRepository
import com.vtempe.shared.domain.repository.ProfileRepository
import com.vtempe.ui.presenter.SleepPresenter
import com.vtempe.ui.presenter.SleepPresenterDelegate
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

private class IosSleepPresenter(
    aiTrainerRepository: AiTrainerRepository,
    profileRepository: ProfileRepository,
    sleepStore: SleepStore,
) : SleepPresenter {
    private val job = SupervisorJob()
    private val scope = CoroutineScope(Dispatchers.Main + job)
    private val delegate = SleepPresenterDelegate(
        aiTrainerRepository = aiTrainerRepository,
        profileRepository = profileRepository,
        sleepStore = sleepStore,
        scope = scope
    )
    override val state get() = delegate.state
    override fun sync() = delegate.sync()
    override fun logSleep(hours: Int, minutes: Int) = delegate.logSleep(hours, minutes)
    fun close() = job.cancel()
}

@Composable
actual fun rememberSleepPresenter(): SleepPresenter {
    val presenter = remember {
        val koin = requireNotNull(KoinProvider.koin) { "Koin is not started" }
        IosSleepPresenter(
            aiTrainerRepository = koin.get(),
            profileRepository = koin.get(),
            sleepStore = koin.get()
        )
    }
    DisposableEffect(Unit) { onDispose { presenter.close() } }
    return presenter
}
