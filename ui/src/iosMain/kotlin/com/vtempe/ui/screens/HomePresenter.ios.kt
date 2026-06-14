package com.vtempe.ui.screens

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import com.vtempe.shared.data.di.KoinProvider
import com.vtempe.shared.data.repo.SleepStore
import com.vtempe.shared.data.repo.WeightStore
import com.vtempe.shared.domain.repository.NutritionRepository
import com.vtempe.shared.domain.repository.TrainingRepository
import com.vtempe.shared.domain.usecase.EnsureCoachData
import com.vtempe.ui.presenter.HomePresenter
import com.vtempe.ui.presenter.HomePresenterDelegate
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

private class IosHomePresenter(
    trainingRepository: TrainingRepository,
    nutritionRepository: NutritionRepository,
    ensureCoachData: EnsureCoachData,
    sleepStore: SleepStore,
    weightStore: WeightStore,
) : HomePresenter {
    private val job = SupervisorJob()
    private val scope = CoroutineScope(Dispatchers.Main + job)
    private val delegate = HomePresenterDelegate(
        trainingRepository = trainingRepository,
        nutritionRepository = nutritionRepository,
        ensureCoachData = ensureCoachData,
        sleepStore = sleepStore,
        weightStore = weightStore,
        scope = scope
    )
    override val state get() = delegate.state
    override fun logWeight(kg: Double) = delegate.logWeight(kg)
    override fun dismissWeightCheckin() = delegate.dismissWeightCheckin()
    fun close() = job.cancel()
}

@Composable
actual fun rememberHomePresenter(): HomePresenter {
    val presenter = remember {
        val koin = requireNotNull(KoinProvider.koin) { "Koin is not started" }
        IosHomePresenter(
            trainingRepository = koin.get(),
            nutritionRepository = koin.get(),
            ensureCoachData = koin.get(),
            sleepStore = koin.get(),
            weightStore = koin.get()
        )
    }
    DisposableEffect(Unit) { onDispose { presenter.close() } }
    return presenter
}
