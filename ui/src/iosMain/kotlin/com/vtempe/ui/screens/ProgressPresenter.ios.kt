package com.vtempe.ui.screens

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import com.vtempe.shared.domain.repository.NutritionRepository
import com.vtempe.shared.domain.repository.TrainingRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import com.vtempe.shared.data.di.KoinProvider

private class IosProgressPresenter(
    private val trainingRepository: TrainingRepository,
    private val nutritionRepository: NutritionRepository
) : ProgressPresenter {
    private val job = SupervisorJob()
    private val scope = CoroutineScope(Dispatchers.Main + job)

    private val mutableState = MutableStateFlow(ProgressState())
    override val state: StateFlow<ProgressState> = mutableState

    init {
        scope.launch {
            combine(
                trainingRepository.observeWorkouts(),
                nutritionRepository.observePlan()
            ) { workouts, nutritionPlan ->
                buildProgressState(workouts, nutritionPlan)
            }.collectLatest { progress ->
                mutableState.value = progress
            }
        }
    }

    fun close() {
        job.cancel()
    }
}

@Composable
actual fun rememberProgressPresenter(): ProgressPresenter {
    val presenter = remember {
        val koin = requireNotNull(KoinProvider.koin) { "Koin is not started" }
        IosProgressPresenter(
            trainingRepository = koin.get<TrainingRepository>(),
            nutritionRepository = koin.get<NutritionRepository>()
        )
    }
    DisposableEffect(Unit) { onDispose { presenter.close() } }
    return presenter
}
