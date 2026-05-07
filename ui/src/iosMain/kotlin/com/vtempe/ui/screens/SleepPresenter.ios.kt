package com.vtempe.ui.screens

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import com.vtempe.shared.domain.repository.AdviceRepository
import com.vtempe.shared.domain.repository.ProfileRepository
import com.vtempe.shared.data.di.KoinProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.StateFlow

private class IosSleepPresenter(
    adviceRepository: AdviceRepository,
    profileRepository: ProfileRepository,
) : SleepPresenter {

    private val job = SupervisorJob()
    private val scope = CoroutineScope(Dispatchers.Main + job)

    private val delegate = SleepPresenterDelegate(
        adviceRepository = adviceRepository,
        profileRepository = profileRepository,
        scope = scope
    )

    override val state: StateFlow<SleepState> = delegate.state

    override fun sync() = delegate.sync()

    fun close() = job.cancel()
}

@Composable
actual fun rememberSleepPresenter(): SleepPresenter {
    val presenter = remember {
        val koin = requireNotNull(KoinProvider.koin) { "Koin is not started" }
        IosSleepPresenter(
            adviceRepository = koin.get<AdviceRepository>(),
            profileRepository = koin.get<ProfileRepository>()
        )
    }
    DisposableEffect(Unit) { onDispose { presenter.close() } }
    return presenter
}
