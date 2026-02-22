package com.vtempe.ui.vm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vtempe.shared.domain.repository.NutritionRepository
import com.vtempe.shared.domain.usecase.EnsureCoachData
import com.vtempe.ui.screens.NutritionPresenter
import com.vtempe.ui.screens.NutritionState
import com.vtempe.ui.screens.resolveNutritionSelectedDay
import com.vtempe.ui.state.UiState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

class NutritionViewModel(
    private val ensureCoachData: EnsureCoachData,
    private val nutritionRepository: NutritionRepository
) : ViewModel(), NutritionPresenter {
    private val _state = MutableStateFlow(NutritionState())
    override val state: StateFlow<NutritionState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            nutritionRepository.observePlan().collect { plan ->
                if (plan != null) {
                    _state.value = _state.value.copy(
                        ui = UiState.Data(plan),
                        selectedDay = resolveNutritionSelectedDay(
                            selectedDay = _state.value.selectedDay,
                            availableDays = plan.mealsByDay.keys
                        )
                    )
                } else if (_state.value.ui !is UiState.Data) {
                    _state.value = _state.value.copy(ui = UiState.Loading)
                }
            }
        }
        refresh(force = false)
    }

    override fun refresh(weekIndex: Int, force: Boolean) {
        viewModelScope.launch {
            if (_state.value.ui !is UiState.Data) {
                _state.value = _state.value.copy(ui = UiState.Loading)
            }
            val result = runCatching { ensureCoachData(weekIndex, force = force) }
            val success = result.getOrDefault(false)
            if (result.isFailure || (!success && _state.value.ui !is UiState.Data)) {
                val message = result.exceptionOrNull()?.message ?: "Unable to load plan"
                _state.value = _state.value.copy(ui = UiState.Error(message))
            }
        }
    }

    override fun selectDay(day: String) { _state.value = _state.value.copy(selectedDay = day) }
}


