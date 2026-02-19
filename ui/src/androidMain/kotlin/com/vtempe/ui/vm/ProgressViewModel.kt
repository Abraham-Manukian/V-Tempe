package com.vtempe.ui.vm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vtempe.shared.domain.repository.NutritionRepository
import com.vtempe.shared.domain.repository.TrainingRepository
import com.vtempe.ui.screens.ProgressPresenter
import com.vtempe.ui.screens.ProgressState
import com.vtempe.ui.screens.buildProgressState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class ProgressViewModel(
    private val trainingRepository: TrainingRepository,
    private val nutritionRepository: NutritionRepository
) : ViewModel(), ProgressPresenter {
    private val _state = MutableStateFlow(ProgressState())
    override val state: StateFlow<ProgressState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            combine(
                trainingRepository.observeWorkouts(),
                nutritionRepository.observePlan()
            ) { workouts, nutritionPlan ->
                buildProgressState(workouts, nutritionPlan)
            }.collectLatest { progress ->
                _state.value = progress
            }
        }
    }
}
