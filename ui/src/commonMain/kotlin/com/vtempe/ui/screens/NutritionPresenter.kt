package com.vtempe.ui.screens

import androidx.compose.runtime.Composable
import com.vtempe.shared.domain.model.NutritionPlan
import com.vtempe.ui.state.UiState
import kotlinx.coroutines.flow.StateFlow

data class NutritionState(
    val ui: UiState<NutritionPlan> = UiState.Loading,
    val selectedDay: String = currentWeekdayKey()
)

interface NutritionPresenter {
    val state: StateFlow<NutritionState>
    fun refresh(weekIndex: Int = 0, force: Boolean = true)
    fun selectDay(day: String)
}

@Composable
expect fun rememberNutritionPresenter(): NutritionPresenter

