package com.vtempe.ui.presenter

import androidx.compose.runtime.Immutable
import com.vtempe.shared.domain.model.Workout
import com.vtempe.shared.domain.repository.TrainingRepository
import com.vtempe.shared.domain.usecase.EnsureCoachData
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
    val totalVolume: Int = 0,
    val sleepMinutes: Int = 0,
    val loading: Boolean = false,
)

interface HomePresenter {
    val state: StateFlow<HomeState>
}

class HomePresenterDelegate(
    private val trainingRepository: TrainingRepository,
    private val ensureCoachData: EnsureCoachData,
    private val scope: CoroutineScope,
) : HomePresenter {

    private val _state = MutableStateFlow(HomeState(loading = true))
    override val state: StateFlow<HomeState> = _state.asStateFlow()

    init {
        trainingRepository.observeWorkouts()
            .onEach { workouts -> _state.update { buildHomeState(workouts) } }
            .catch { Napier.e("HomePresenter observe error", it) }
            .launchIn(scope)

        scope.launch {
            runCatching { ensureCoachData() }
                .onFailure { Napier.w("EnsureCoachData failed on Home", it) }
        }
    }

    private fun buildHomeState(workouts: List<Workout>): HomeState {
        val today = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date
        val todayWorkouts = workouts.filter { it.date == today }
        val todaySets = todayWorkouts.sumOf { it.sets.size }
        val totalVolume = workouts.sumOf { w ->
            w.sets.sumOf { set -> ((set.weightKg ?: 0.0) * set.reps).toInt() }
        }
        return HomeState(
            workouts = workouts,
            todaySets = todaySets,
            totalVolume = totalVolume,
            loading = false
        )
    }
}
