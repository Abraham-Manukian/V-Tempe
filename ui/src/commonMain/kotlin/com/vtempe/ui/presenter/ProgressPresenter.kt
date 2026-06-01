package com.vtempe.ui.presenter

import androidx.compose.runtime.Immutable
import com.vtempe.shared.domain.model.Meal
import com.vtempe.shared.domain.model.NutritionPlan
import com.vtempe.shared.domain.model.Workout
import com.vtempe.shared.domain.repository.CoachCacheRepository
import com.vtempe.shared.domain.repository.NutritionRepository
import com.vtempe.shared.domain.repository.TrainingRepository
import com.vtempe.shared.domain.util.CoachSchedule
import io.github.aakira.napier.Napier
import com.vtempe.ui.util.toShortKey
import com.vtempe.ui.util.toWeekIndex
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
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
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
    /** First day of week 0 — set once on first bootstrap, never changes. */
    epochDate: LocalDate?,
): ProgressState {
    // Only consider workouts from the registration date onwards.
    // This filters out leftover data from development testing and prevents
    // pre-fetched future-week workouts from appearing as dots on the calendar.
    val validWorkouts = if (epochDate != null)
        workouts.filter { it.date >= epochDate }
    else
        workouts

    val totalSets   = validWorkouts.sumOf { it.sets.size }
    val totalVolume = validWorkouts.sumOf { w -> w.sets.sumOf { ((it.weightKg ?: 0.0) * it.reps).toInt() } }

    val weeklyVolumesArray = IntArray(7) { 0 }
    validWorkouts.forEach { workout ->
        val dayVolume = workout.sets.sumOf { set -> ((set.weightKg ?: 0.0) * set.reps).toInt() }
        weeklyVolumesArray[workout.date.dayOfWeek.toWeekIndex()] += dayVolume
    }

    val recentWeightSeries = validWorkouts
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

    // Calendar dots: only show dates the user actually registered for and up to today.
    // Excludes pre-fetched future weeks from showing as future dots.
    val workoutDates = validWorkouts
        .map { it.date }
        .filter { it <= today }
        .toSet()

    val dayWorkouts = if (selectedDate != null)
        validWorkouts.filter { it.date == selectedDate }
    else
        emptyList()

    // Show nutrition only for dates within the registered plan period.
    // Prevents the plan from appearing on arbitrary past dates (the plan is keyed
    // by day-of-week, so without this guard every Monday/Tuesday/... looks like
    // it has meals — even before the user registered).
    val dateIsInPlanRange = selectedDate != null &&
            nutritionPlan != null &&
            (epochDate == null || selectedDate >= epochDate) &&
            selectedDate <= today
    val dayMeals = if (dateIsInPlanRange) {
        nutritionPlan!!.mealsByDay[selectedDate!!.dayOfWeek.toShortKey()].orEmpty()
    } else emptyList()

    return ProgressState(
        totalWorkouts = validWorkouts.size,
        totalSets     = totalSets,
        totalVolume   = totalVolume,
        weeklyVolumes = weeklyVolumesArray.toList(),
        weightSeries  = recentWeightSeries,
        caloriesSeries = caloriesSeries,
        sleepHoursWeek = emptyList(),
        today            = today,
        calendarYear     = calendarYearMonth.first,
        calendarMonth    = calendarYearMonth.second,
        workoutDates     = workoutDates,
        selectedDate     = selectedDate,
        dayWorkouts      = dayWorkouts,
        dayMeals         = dayMeals,
    )
}


class ProgressPresenterDelegate(
    private val trainingRepository: TrainingRepository,
    private val nutritionRepository: NutritionRepository,
    private val coachCache: CoachCacheRepository,
    private val scope: CoroutineScope,
) : ProgressPresenter {

    private val today: LocalDate = Clock.System.todayIn(TimeZone.currentSystemDefault())

    /** The date of the very first bootstrap — week 0 day 0. Null until first bootstrap. */
    private val epochDate: LocalDate? = coachCache.planEpochDateMs()?.let { ms ->
        Instant.fromEpochMilliseconds(ms)
            .toLocalDateTime(TimeZone.currentSystemDefault()).date
    }

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
            buildProgressState(workouts, plan, today, yearMonth, selectedDate, epochDate)
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
