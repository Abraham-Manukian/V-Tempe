package com.vtempe.ui.presenter

import androidx.compose.runtime.Immutable
import com.vtempe.shared.data.repo.SleepStore
import com.vtempe.shared.data.repo.WeightStore
import com.vtempe.shared.domain.model.Macros
import com.vtempe.shared.domain.model.NutritionPlan
import com.vtempe.shared.domain.model.Workout
import com.vtempe.shared.domain.repository.NutritionRepository
import com.vtempe.shared.domain.repository.TrainingRepository
import com.vtempe.shared.domain.usecase.EnsureCoachData
import com.vtempe.ui.util.toShortKey
import io.github.aakira.napier.Napier
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/** Today's planned workout as the Home card describes it. */
@Immutable
data class TodayWorkout(val label: String, val exercises: Int, val sets: Int)

@Immutable
data class HomeState(
    val workouts: List<Workout> = emptyList(),
    val todaySets: Int = 0,
    val todayExercisesCount: Int = 0,
    val dailyKcalPlan: Int = 0,
    val sleepMinutes: Int = 0,
    val loading: Boolean = false,
    /** True when the user hasn't logged their weight this week. */
    val showWeightCheckin: Boolean = false,
    /** Last known weight (kg) to pre-fill the check-in field. Null = never logged. */
    val lastWeightKg: Double? = null,
    /** Briefly true after a weight is saved to show a confirmation. */
    val weightSaved: Boolean = false,
    /** Non-null when the last data/plan refresh failed. Cleared on the next successful load. */
    val errorMessage: String? = null,
    /** Today's workout from the stored plan; null on a rest day or without a plan (VT-AUD-069). */
    val todayWorkout: TodayWorkout? = null,
    val hasTrainingPlan: Boolean = false,
    /** Sum of today's planned meals; null when there is no menu for today. */
    val todayMacros: Macros? = null,
)

interface HomePresenter {
    val state: StateFlow<HomeState>
    fun refresh()
    fun logWeight(kg: Double)
    fun dismissWeightCheckin()
}

class HomePresenterDelegate(
    private val trainingRepository: TrainingRepository,
    private val nutritionRepository: NutritionRepository,
    private val ensureCoachData: EnsureCoachData,
    private val sleepStore: SleepStore,
    private val weightStore: WeightStore,
    private val scope: CoroutineScope,
) : HomePresenter {

    private val _state = MutableStateFlow(HomeState(loading = true))
    override val state: StateFlow<HomeState> = _state.asStateFlow()

    // Cached so each flow can update state independently without waiting for the other
    private var cachedWorkouts: List<Workout> = emptyList()
    private var cachedPlan: NutritionPlan? = null

    // Tracked per-source so one source recovering can't silently clear an error that's still
    // live on a different, independent source (e.g. ensureCoachData succeeding right after
    // observeWorkouts() errored must not hide the workouts error).
    private var workoutsError = false
    private var planError = false
    private var coachDataError = false
    // A failed flow ends its collector; Retry starts a new one (VT-AUD-071).
    private var workoutsJob: Job? = null
    private var planJob: Job? = null

    init {
        // Populate weight check-in and last known weight up front (no IO needed)
        _state.update {
            it.copy(
                showWeightCheckin = weightStore.shouldShowCheckin(),
                lastWeightKg = weightStore.latestWeight()
            )
        }

        observeWorkouts()
        observePlan()

        scope.launch {
            runCatching { ensureCoachData() }
                .onSuccess { coachDataError = false; _state.update { it.copy(errorMessage = combinedErrorMessage()) } }
                .onFailure { e ->
                    Napier.w("EnsureCoachData failed on Home", e)
                    coachDataError = true
                    _state.update { it.copy(errorMessage = combinedErrorMessage()) }
                }
        }
    }

    private fun observeWorkouts() {
        workoutsJob?.cancel()
        workoutsJob = trainingRepository.observeWorkouts()
            .onEach { workouts ->
                cachedWorkouts = workouts
                workoutsError = false
                _state.update { buildHomeState(cachedWorkouts, cachedPlan).copy(errorMessage = combinedErrorMessage()) }
            }
            .catch { e ->
                Napier.e("HomePresenter workouts error", e)
                workoutsError = true
                _state.update { it.copy(loading = false, errorMessage = combinedErrorMessage()) }
            }
            .launchIn(scope)
    }

    private fun observePlan() {
        planJob?.cancel()
        planJob = nutritionRepository.observePlan()
            .onEach { plan ->
                cachedPlan = plan
                planError = false
                _state.update { buildHomeState(cachedWorkouts, cachedPlan).copy(errorMessage = combinedErrorMessage()) }
            }
            .catch { e ->
                Napier.e("HomePresenter plan error", e)
                planError = true
                _state.update { it.copy(loading = false, errorMessage = combinedErrorMessage()) }
            }
            .launchIn(scope)
    }

    override fun refresh() {
        // Re-read what failed, not only regenerate: a finished collector never recovers by itself.
        if (workoutsError || workoutsJob?.isActive != true) observeWorkouts()
        if (planError || planJob?.isActive != true) observePlan()
        scope.launch {
            runCatching { ensureCoachData() }
                .onSuccess { coachDataError = false; _state.update { it.copy(errorMessage = combinedErrorMessage()) } }
                .onFailure { e ->
                    Napier.w("EnsureCoachData failed on Home refresh", e)
                    coachDataError = true
                    _state.update { it.copy(errorMessage = combinedErrorMessage()) }
                }
        }
    }

    private fun combinedErrorMessage(): String? =
        if (workoutsError || planError || coachDataError) "load_failed" else null

    override fun logWeight(kg: Double) {
        val today = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date
        weightStore.logWeight(today.toString(), kg)
        _state.update { it.copy(showWeightCheckin = false, lastWeightKg = kg, weightSaved = true) }
        scope.launch {
            kotlinx.coroutines.delay(2_000)
            _state.update { it.copy(weightSaved = false) }
        }
    }

    override fun dismissWeightCheckin() {
        weightStore.dismissCheckin()
        _state.update { it.copy(showWeightCheckin = false) }
    }

    private fun buildHomeState(workouts: List<Workout>, plan: NutritionPlan?): HomeState {
        val today = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date
        val todayWorkouts = workouts.filter { it.date == today }
        // Each WorkoutSet is one exercise with its prescribed number of sets.
        val todaySets = todayWorkouts.sumOf { workout -> workout.sets.sumOf { it.sets } }
        // If today has a workout, show its exercise count.
        // Fall back to the week's total exercise variety so the stat is never zero on rest days.
        val todayExercisesCount = todayWorkouts
            .flatMap { it.sets }.map { it.exerciseId }.distinct().size
            .takeIf { it > 0 }
            ?: workouts.flatMap { it.sets }.map { it.exerciseId }.distinct().size
        val dayKey = today.dayOfWeek.toShortKey()
        val todayMeals = plan?.mealsByDay?.get(dayKey).orEmpty()
        val dailyKcalPlan = todayMeals.sumOf { it.kcal }
        val sleepMinutes = sleepStore.getForDate(today.toString())
        val todayWorkout = todayWorkouts.takeIf { it.isNotEmpty() }?.let { list ->
            TodayWorkout(
                label = list.map { it.label }.firstOrNull { it.isNotBlank() }.orEmpty(),
                exercises = list.flatMap { it.sets }.map { it.exerciseId }.distinct().size,
                sets = todaySets,
            )
        }
        val todayMacros = todayMeals.takeIf { it.isNotEmpty() }?.let { meals ->
            Macros(
                proteinGrams = meals.sumOf { it.macros.proteinGrams },
                fatGrams = meals.sumOf { it.macros.fatGrams },
                carbsGrams = meals.sumOf { it.macros.carbsGrams },
                kcal = dailyKcalPlan,
            )
        }
        return _state.value.copy(
            todayWorkout = todayWorkout,
            hasTrainingPlan = workouts.isNotEmpty(),
            todayMacros = todayMacros,
            workouts = workouts,
            todaySets = todaySets,
            todayExercisesCount = todayExercisesCount,
            dailyKcalPlan = dailyKcalPlan,
            sleepMinutes = sleepMinutes,
            loading = false
        )
    }
}
