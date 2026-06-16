package com.vtempe.ui.presenter

import androidx.compose.runtime.Immutable
import com.vtempe.shared.data.repo.SleepStore
import com.vtempe.shared.data.repo.WeightStore
import com.vtempe.shared.domain.model.NutritionPlan
import com.vtempe.shared.domain.model.Workout
import com.vtempe.shared.domain.repository.NutritionRepository
import com.vtempe.shared.domain.repository.TrainingRepository
import com.vtempe.shared.domain.usecase.EnsureCoachData
import com.vtempe.ui.util.toShortKey
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
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

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

    init {
        // Populate weight check-in and last known weight up front (no IO needed)
        _state.update {
            it.copy(
                showWeightCheckin = weightStore.shouldShowCheckin(),
                lastWeightKg = weightStore.latestWeight()
            )
        }

        trainingRepository.observeWorkouts()
            .onEach { workouts ->
                cachedWorkouts = workouts
                _state.update { buildHomeState(cachedWorkouts, cachedPlan) }
            }
            .catch { Napier.e("HomePresenter workouts error", it) }
            .launchIn(scope)

        nutritionRepository.observePlan()
            .onEach { plan ->
                cachedPlan = plan
                _state.update { buildHomeState(cachedWorkouts, cachedPlan) }
            }
            .catch { Napier.e("HomePresenter plan error", it) }
            .launchIn(scope)

        scope.launch {
            runCatching { ensureCoachData() }
                .onFailure { Napier.w("EnsureCoachData failed on Home", it) }
        }
    }

    override fun refresh() {
        scope.launch {
            runCatching { ensureCoachData() }
                .onFailure { Napier.w("EnsureCoachData failed on Home refresh", it) }
        }
    }

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
        val todaySets = todayWorkouts.sumOf { it.sets.size }
        // If today has a workout, show its exercise count.
        // Fall back to the week's total exercise variety so the stat is never zero on rest days.
        val todayExercisesCount = todayWorkouts
            .flatMap { it.sets }.map { it.exerciseId }.distinct().size
            .takeIf { it > 0 }
            ?: workouts.flatMap { it.sets }.map { it.exerciseId }.distinct().size
        val dayKey = today.dayOfWeek.toShortKey()
        val dailyKcalPlan = plan?.mealsByDay?.get(dayKey)?.sumOf { it.kcal } ?: 0
        val sleepMinutes = sleepStore.getForDate(today.toString())
        return _state.value.copy(
            workouts = workouts,
            todaySets = todaySets,
            todayExercisesCount = todayExercisesCount,
            dailyKcalPlan = dailyKcalPlan,
            sleepMinutes = sleepMinutes,
            loading = false
        )
    }
}
