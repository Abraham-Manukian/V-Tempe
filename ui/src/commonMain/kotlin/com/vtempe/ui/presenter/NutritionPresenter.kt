package com.vtempe.ui.presenter

import androidx.compose.runtime.Immutable
import com.vtempe.shared.domain.model.NutritionPlan
import com.vtempe.shared.domain.repository.CoachCacheRepository
import com.vtempe.shared.domain.repository.NutritionRepository
import com.vtempe.shared.domain.usecase.EnsureCoachData
import com.vtempe.shared.domain.util.CoachSchedule
import com.vtempe.shared.domain.util.DataResult
import com.vtempe.ui.state.UiState
import io.github.aakira.napier.Napier
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import com.vtempe.ui.util.toShortKey
import kotlinx.datetime.Clock
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

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

fun currentWeekdayKey(): String {
    val day = Clock.System.now()
        .toLocalDateTime(TimeZone.currentSystemDefault())
        .date
        .dayOfWeek
    return day.toShortKey()
}

fun resolveNutritionSelectedDay(selectedDay: String, availableDays: Set<String>): String {
    if (selectedDay in availableDays) return selectedDay
    val today = currentWeekdayKey()
    if (today in availableDays) return today
    val weekOrder = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
    return weekOrder.firstOrNull { it in availableDays } ?: selectedDay
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
    fun refresh(force: Boolean = false)
    fun selectDay(day: String)
}

class NutritionPresenterDelegate(
    private val ensureCoachData: EnsureCoachData,
    private val nutritionRepository: NutritionRepository,
    private val coachCache: CoachCacheRepository,
    private val scope: CoroutineScope,
) : NutritionPresenter {

    private val _state = MutableStateFlow(NutritionState())
    override val state: StateFlow<NutritionState> = _state.asStateFlow()

    init {
        nutritionRepository.observePlan()
            .onEach { plan ->
                if (plan != null) {
                    val selectedDay = resolveNutritionSelectedDay(
                        _state.value.selectedDay,
                        plan.mealsByDay.keys
                    )
                    _state.update {
                        it.copy(
                            ui = UiState.Data(plan),
                            selectedDay = selectedDay,
                            dayMacros = computeDayMacros(plan, selectedDay),
                            weekMacros = computeWeekMacros(plan)
                        )
                    }
                }
            }
            .catch { Napier.e("NutritionPresenter observe error", it) }
            .launchIn(scope)

        refresh(force = false)
    }

    override fun refresh(force: Boolean) {
        scope.launch {
            val weekIndex = CoachSchedule.currentWeekIndex(coachCache.planEpochDateMs())

            if (force) {
                // Register as active week WITHOUT loading stale cached data into the flow —
                // we're about to overwrite it. persistPlan will push the new plan once ready.
                nutritionRepository.registerActiveWeek(weekIndex)
                _state.update { it.copy(ui = UiState.Loading) }
            } else {
                // Fast path: setActiveWeek loads cached DB plan into planFlow immediately.
                // The observer above fires → UiState.Data before the network even starts.
                val hasCached = nutritionRepository.setActiveWeek(weekIndex)
                if (!hasCached) {
                    _state.update { it.copy(ui = UiState.Loading) }
                }
            }

            runCatching { ensureCoachData(force = force) }
                .onFailure { e ->
                    Napier.e("NutritionPresenter refresh failed", e)
                    if (_state.value.ui !is UiState.Data) {
                        _state.update { it.copy(ui = UiState.Error(e.message)) }
                    }
                }
        }
    }

    override fun selectDay(day: String) {
        val plan = (_state.value.ui as? UiState.Data)?.value ?: return
        _state.update {
            it.copy(
                selectedDay = day,
                dayMacros = computeDayMacros(plan, day)
            )
        }
    }
}
