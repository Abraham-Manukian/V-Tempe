package com.vtempe.ui.screens

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import com.vtempe.shared.domain.model.NutritionPlan
import com.vtempe.ui.state.UiState
import kotlinx.coroutines.flow.StateFlow

@Immutable
data class MacroTotals(
    val protein: Int = 0,
    val fat: Int = 0,
    val carbs: Int = 0,
    val kcal: Int = 0
) {
    companion object {
        val EMPTY = MacroTotals()
    }
}

fun computeDayMacros(plan: NutritionPlan, day: String): MacroTotals {
    val meals = plan.mealsByDay[day].orEmpty()
    return MacroTotals(
        protein = meals.sumOf { it.macros.proteinGrams },
        fat = meals.sumOf { it.macros.fatGrams },
        carbs = meals.sumOf { it.macros.carbsGrams },
        kcal = meals.sumOf { it.macros.kcal }
    )
}

fun computeWeekMacros(plan: NutritionPlan): MacroTotals {
    val meals = plan.mealsByDay.values.flatten()
    return MacroTotals(
        protein = meals.sumOf { it.macros.proteinGrams },
        fat = meals.sumOf { it.macros.fatGrams },
        carbs = meals.sumOf { it.macros.carbsGrams },
        kcal = meals.sumOf { it.macros.kcal }
    )
}

@Immutable
data class NutritionState(
    val ui: UiState<NutritionPlan> = UiState.Loading,
    val selectedDay: String = currentWeekdayKey(),
    val dayMacros: MacroTotals = MacroTotals.EMPTY,
    val weekMacros: MacroTotals = MacroTotals.EMPTY
)

interface NutritionPresenter {
    val state: StateFlow<NutritionState>
    fun refresh(weekIndex: Int = 0, force: Boolean = true)
    fun selectDay(day: String)
}

@Composable
expect fun rememberNutritionPresenter(): NutritionPresenter

