package com.vtempe.ui.screens

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import com.vtempe.shared.domain.repository.NutritionRepository
import com.vtempe.shared.domain.usecase.EnsureCoachData
import com.vtempe.ui.state.UiState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import com.vtempe.shared.data.di.KoinProvider

private class IosNutritionPresenter(
    private val ensureCoachData: EnsureCoachData,
    private val nutritionRepository: NutritionRepository,
) : NutritionPresenter {
    private val job = SupervisorJob()
    private val scope = CoroutineScope(Dispatchers.Main + job)

    private val mutableState = MutableStateFlow(NutritionState())
    override val state: StateFlow<NutritionState> = mutableState

    init {
        scope.launch {
            nutritionRepository.observePlan().collect { plan ->
                if (plan != null) {
                    mutableState.value = mutableState.value.copy(
                        ui = UiState.Data(plan),
                        selectedDay = resolveNutritionSelectedDay(
                            selectedDay = mutableState.value.selectedDay,
                            availableDays = plan.mealsByDay.keys
                        )
                    )
                } else if (mutableState.value.ui !is UiState.Data) {
                    mutableState.value = mutableState.value.copy(ui = UiState.Loading)
                }
            }
        }
        refresh(force = false)
    }

    override fun refresh(weekIndex: Int, force: Boolean) {
        scope.launch {
            if (mutableState.value.ui !is UiState.Data) {
                mutableState.value = mutableState.value.copy(ui = UiState.Loading)
            }
            val result = runCatching { ensureCoachData(weekIndex, force = force) }
            val success = result.getOrDefault(false)
            if (result.isFailure || (!success && mutableState.value.ui !is UiState.Data)) {
                val message = result.exceptionOrNull()?.message ?: "Unable to load plan"
                mutableState.value = mutableState.value.copy(ui = UiState.Error(message))
            }
        }
    }

    override fun selectDay(day: String) {
        mutableState.value = mutableState.value.copy(selectedDay = day)
    }

    fun close() {
        job.cancel()
    }
}

@Composable
actual fun rememberNutritionPresenter(): NutritionPresenter {
    val presenter = remember {
        val koin = requireNotNull(KoinProvider.koin) { "Koin is not started" }
        IosNutritionPresenter(
            ensureCoachData = koin.get<EnsureCoachData>(),
            nutritionRepository = koin.get<NutritionRepository>()
        )
    }
    DisposableEffect(Unit) { onDispose { presenter.close() } }
    return presenter
}

