package com.vtempe.ui.screens

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import com.vtempe.shared.data.di.KoinProvider
import com.vtempe.shared.domain.repository.TrainingRepository
import com.vtempe.shared.domain.usecase.EnsureCoachData
import com.vtempe.ui.presenter.HomePresenter
import com.vtempe.ui.presenter.HomePresenterDelegate
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

private class IosHomePresenter(trainingRepository: TrainingRepository, ensureCoachData: EnsureCoachData) : HomePresenter {
    private val job = SupervisorJob()
    private val scope = CoroutineScope(Dispatchers.Main + job)
    private val delegate = HomePresenterDelegate(trainingRepository = trainingRepository, ensureCoachData = ensureCoachData, scope = scope)
    override val state get() = delegate.state
    fun close() = job.cancel()
}

@Composable
actual fun rememberHomePresenter(): HomePresenter {
    val presenter = remember {
        val koin = requireNotNull(KoinProvider.koin) { "Koin is not started" }
        IosHomePresenter(trainingRepository = koin.get(), ensureCoachData = koin.get())
    }
    DisposableEffect(Unit) { onDispose { presenter.close() } }
    return presenter
}
