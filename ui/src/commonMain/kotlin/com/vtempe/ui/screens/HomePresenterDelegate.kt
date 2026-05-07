package com.vtempe.ui.screens

import com.vtempe.shared.domain.repository.TrainingRepository
import com.vtempe.shared.domain.usecase.EnsureCoachData
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * Shared business logic for the home screen.
 * Used by [HomeViewModel] (Android) and [IosHomePresenter] (iOS).
 * Each platform supplies its own [CoroutineScope].
 */
class HomePresenterDelegate(
    private val trainingRepository: TrainingRepository,
    private val ensureCoachData: EnsureCoachData,
    private val scope: CoroutineScope
) {
    private val _state = MutableStateFlow(HomeState(loading = true))
    val state: StateFlow<HomeState> = _state.asStateFlow()

    init {
        scope.launch { runCatching { ensureCoachData() } }
        scope.launch {
            trainingRepository.observeWorkouts().collectLatest { list ->
                val sets = list.firstOrNull()?.sets?.size ?: 0
                val volume = list.firstOrNull()?.sets
                    ?.sumOf { ((it.weightKg ?: 0.0) * it.reps).toInt() } ?: 0
                _state.value = HomeState(
                    workouts = list,
                    todaySets = sets,
                    totalVolume = volume,
                    sleepMinutes = _state.value.sleepMinutes,
                    loading = false
                )
            }
        }
    }
}
