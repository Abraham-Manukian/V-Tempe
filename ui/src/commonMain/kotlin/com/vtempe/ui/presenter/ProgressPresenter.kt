package com.vtempe.ui.presenter

import androidx.compose.runtime.Immutable
import com.vtempe.shared.domain.model.Meal
import com.vtempe.shared.domain.model.NutritionPlan
import com.vtempe.shared.domain.model.Workout
import com.vtempe.shared.domain.repository.NutritionRepository
import com.vtempe.shared.domain.repository.TrainingRepository
import io.github.aakira.napier.Napier
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.datetime.Clock
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn

@Immutable
data class ProgressState(
    val totalWorkouts: Int = 0,
    val totalSets: Int = 0,
    val totalVolume: Int = 0,
    val weeklyVolumes: List<Int> = List(7) { 0 },
    val weightSeries: List<Float> = emptyList(),
    val caloriesSeries: List<Int> = emptyList(),
    val sleepHoursWeek: List<Int> = emptyList(),
    // calendar
    val today: LocalDate = LocalDate(2000, 1, 1),
    val calendarYear: Int = 2000,
    val calendarMonth: Int = 1,
    val workoutDates: Set<LocalDate> = emptySet(),
    val selectedDate: LocalDate? = null,
    val dayWorkouts: List<Workout> = emptyList(),
    val dayMeals: List<Meal> = emptyList(),
)

interface ProgressPresenter {
    val state: StateFlow<ProgressState>
    fun selectDate(date: LocalDate) {}
    fun clearDate() {}
    fun prevMonth() {}
    fun nextMonth() {}
}

internal fun buildProgressState(
    workouts: List<Workout>,
    nutritionPlan: NutritionPlan?,
    today: LocalDate,
    calendarYearMonth: Pair<Int, Int>,
    selectedDate: LocalDate?,
): ProgressState {
    val totalSets = workouts.sumOf { it.sets.size }
    val totalVolume = workouts.sumOf { w -> w.sets.sumOf { ((it.weightKg ?: 0.0) * it.reps).toInt() } }

    val weeklyVolumesArray = IntArray(7) { 0 }
    workouts.forEach { workout ->
        val dayVolume = workout.sets.sumOf { set -> ((set.weightKg ?: 0.0) * set.reps).toInt() }
        weeklyVolumesArray[workout.date.dayOfWeek.weekIndex()] += dayVolume
    }

    val recentWeightSeries = workouts
        .sortedBy { it.date }
        .takeLast(14)
        .mapNotNull { workout ->
            val nonNullWeights = workout.sets.mapNotNull { it.weightKg }
            if (nonNullWeights.isEmpty()) null else nonNullWeights.average().toFloat()
        }

    val caloriesSeries = nutritionPlan
        ?.let { plan ->
            listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun").map { day ->
                plan.mealsByDay[day].orEmpty().sumOf { it.kcal }
            }
        }
        .orEmpty()

    val workoutDates = workouts.map { it.date }.toSet()

    val dayWorkouts = if (selectedDate != null) workouts.filter { it.date == selectedDate } else emptyList()
    val dayMeals = if (selectedDate != null && nutritionPlan != null) {
        nutritionPlan.mealsByDay[selectedDate.dayOfWeek.dayKey()].orEmpty()
    } else emptyList()

    return ProgressState(
        totalWorkouts = workouts.size,
        totalSets = totalSets,
        totalVolume = totalVolume,
        weeklyVolumes = weeklyVolumesArray.toList(),
        weightSeries = recentWeightSeries,
        caloriesSeries = caloriesSeries,
        sleepHoursWeek = emptyList(),
        today = today,
        calendarYear = calendarYearMonth.first,
        calendarMonth = calendarYearMonth.second,
        workoutDates = workoutDates,
        selectedDate = selectedDate,
        dayWorkouts = dayWorkouts,
        dayMeals = dayMeals,
    )
}

private fun DayOfWeek.weekIndex(): Int = when (this) {
    DayOfWeek.MONDAY -> 0
    DayOfWeek.TUESDAY -> 1
    DayOfWeek.WEDNESDAY -> 2
    DayOfWeek.THURSDAY -> 3
    DayOfWeek.FRIDAY -> 4
    DayOfWeek.SATURDAY -> 5
    DayOfWeek.SUNDAY -> 6
}

private fun DayOfWeek.dayKey(): String = when (this) {
    DayOfWeek.MONDAY -> "Mon"
    DayOfWeek.TUESDAY -> "Tue"
    DayOfWeek.WEDNESDAY -> "Wed"
    DayOfWeek.THURSDAY -> "Thu"
    DayOfWeek.FRIDAY -> "Fri"
    DayOfWeek.SATURDAY -> "Sat"
    DayOfWeek.SUNDAY -> "Sun"
}

class ProgressPresenterDelegate(
    private val trainingRepository: TrainingRepository,
    private val nutritionRepository: NutritionRepository,
    private val scope: CoroutineScope,
) : ProgressPresenter {

    private val today: LocalDate = Clock.System.todayIn(TimeZone.currentSystemDefault())

    private val _selectedDate = MutableStateFlow<LocalDate?>(null)
    private val _calendarYearMonth = MutableStateFlow(Pair(today.year, today.monthNumber))

    private val _state = MutableStateFlow(ProgressState())
    override val state: StateFlow<ProgressState> = _state.asStateFlow()

    init {
        combine(
            trainingRepository.observeWorkouts(),
            nutritionRepository.observePlan(),
            _selectedDate,
            _calendarYearMonth,
        ) { workouts, plan, selectedDate, yearMonth ->
            buildProgressState(workouts, plan, today, yearMonth, selectedDate)
        }
            .onEach { progressState -> _state.update { progressState } }
            .catch { Napier.e("ProgressPresenter observe error", it) }
            .launchIn(scope)
    }

    override fun selectDate(date: LocalDate) { _selectedDate.value = date }
    override fun clearDate() { _selectedDate.value = null }

    override fun prevMonth() {
        _calendarYearMonth.update { (y, m) ->
            if (m == 1) Pair(y - 1, 12) else Pair(y, m - 1)
        }
    }

    override fun nextMonth() {
        _calendarYearMonth.update { (y, m) ->
            if (m == 12) Pair(y + 1, 1) else Pair(y, m + 1)
        }
    }
}
