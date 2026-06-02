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
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
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
    /** All dates in the active nutrition plan week (Mon–Sun) mapped to calendar dates. */
    val nutritionDates: Set<LocalDate> = emptySet(),
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

    // Workout dots: all scheduled workout dates from registration onward.
    // Include future workouts so user can see upcoming training days.
    val workoutDates = validWorkouts.map { it.date }.toSet()

    // Nutrition dots: all 7 days of the active nutrition plan week.
    // Mapped from plan's week-of-epoch to actual calendar dates.
    val nutritionDates: Set<LocalDate> = if (nutritionPlan != null && epochDate != null) {
        // Find the Monday that starts this plan's week
        val weekStartOffset = nutritionPlan.weekIndex * 7
        val weekMonday = epochDate.plus(DatePeriod(days = weekStartOffset)).let { anchor ->
            // Align to the Monday of the week containing anchor
            val dow = anchor.dayOfWeek.toWeekIndex() // 0=Mon
            anchor.plus(DatePeriod(days = -dow))
        }
        // Map each day key that has meals to an actual calendar date
        val dayOffsets = mapOf(
            "Mon" to 0, "Tue" to 1, "Wed" to 2, "Thu" to 3,
            "Fri" to 4, "Sat" to 5, "Sun" to 6,
        )
        nutritionPlan.mealsByDay.keys
            .filter { nutritionPlan.mealsByDay[it].orEmpty().isNotEmpty() }
            .mapNotNull { key ->
                dayOffsets[key]?.let { offset ->
                    weekMonday.plus(DatePeriod(days = offset))
                }
            }
            .filter { it >= epochDate } // never show pre-registration dates
            .toSet()
    } else emptySet()

    val dayWorkouts = if (selectedDate != null)
        validWorkouts.filter { it.date == selectedDate }
    else
        emptyList()

    // Show nutrition for any selected date that falls within the active plan's week.
    // No longer restricted to past dates — the nutrition plan covers the whole week
    // including future training days.
    val dayMeals = if (selectedDate != null && nutritionPlan != null &&
        (epochDate == null || selectedDate >= epochDate) &&
        selectedDate in nutritionDates
    ) {
        nutritionPlan.mealsByDay[selectedDate.dayOfWeek.toShortKey()].orEmpty()
    } else emptyList()

    return ProgressState(
        totalWorkouts  = validWorkouts.size,
        totalSets      = totalSets,
        totalVolume    = totalVolume,
        weeklyVolumes  = weeklyVolumesArray.toList(),
        weightSeries   = recentWeightSeries,
        caloriesSeries = caloriesSeries,
        sleepHoursWeek = emptyList(),
        today          = today,
        calendarYear   = calendarYearMonth.first,
        calendarMonth  = calendarYearMonth.second,
        workoutDates   = workoutDates,
        nutritionDates = nutritionDates,
        selectedDate   = selectedDate,
        dayWorkouts    = dayWorkouts,
        dayMeals       = dayMeals,
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
