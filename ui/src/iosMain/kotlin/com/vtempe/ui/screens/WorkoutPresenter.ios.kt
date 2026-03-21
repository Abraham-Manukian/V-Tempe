package com.vtempe.ui.screens

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import com.vtempe.shared.domain.model.PerformedSet
import com.vtempe.shared.domain.model.WorkoutProgress
import com.vtempe.shared.domain.model.WorkoutSet
import com.vtempe.shared.domain.repository.TrainingRepository
import com.vtempe.shared.domain.usecase.EnsureCoachData
import com.vtempe.shared.domain.usecase.LogWorkoutSet
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import com.vtempe.shared.data.di.KoinProvider
import kotlinx.datetime.Clock

private class IosWorkoutPresenter(
    private val trainingRepository: TrainingRepository,
    private val logWorkoutSet: LogWorkoutSet,
    private val ensureCoachData: EnsureCoachData,
) : WorkoutPresenter {
    private val job = SupervisorJob()
    private val scope = CoroutineScope(Dispatchers.Main + job)

    private val mutableState = MutableStateFlow(WorkoutState())
    override val state: StateFlow<WorkoutState> = mutableState

    init {
        scope.launch {
            combine(
                trainingRepository.observeWorkouts(),
                trainingRepository.observeWorkoutProgress()
            ) { workouts, progress ->
                workouts to progress
            }.collect { (workouts, progress) ->
                val selected = mutableState.value.selectedWorkoutId
                    ?.takeIf { currentId -> workouts.any { it.id == currentId } }
                    ?: workouts.firstOrNull()?.id
                mutableState.value =
                    mutableState.value.copy(
                        workouts = workouts,
                        selectedWorkoutId = selected,
                        progress = progress
                    )
            }
        }
        scope.launch { runCatching { ensureCoachData() } }
    }

    override fun select(workoutId: String) {
        mutableState.value = mutableState.value.copy(selectedWorkoutId = workoutId)
    }

    override fun addSet(exerciseId: String, reps: Int, weight: Double?, rpe: Double?) {
        val id = mutableState.value.selectedWorkoutId ?: return
        scope.launch { logWorkoutSet(id, WorkoutSet(exerciseId, reps, weight, rpe)) }
    }

    override fun updateNotes(workoutId: String, notes: String) {
        saveProgress(workoutId) { current ->
            current.copy(
                notes = notes.take(500),
                updatedAtEpochMs = Clock.System.now().toEpochMilliseconds(),
                submitted = false
            )
        }
    }

    override fun updatePerformedSet(
        workoutId: String,
        setIndex: Int,
        completed: Boolean,
        actualReps: Int?,
        actualWeightKg: Double?,
        actualRpe: Double?
    ) {
        saveProgress(workoutId) { current ->
            val updatedSets = current.performedSets
                .filterNot { it.setIndex == setIndex }
                .plus(
                    PerformedSet(
                        setIndex = setIndex,
                        completed = completed,
                        actualReps = actualReps,
                        actualWeightKg = actualWeightKg,
                        actualRpe = actualRpe
                    )
                )
                .sortedBy { it.setIndex }
            current.copy(
                performedSets = updatedSets,
                updatedAtEpochMs = Clock.System.now().toEpochMilliseconds(),
                submitted = false
            )
        }
    }

    override fun updateRestSeconds(workoutId: String, restSeconds: Int) {
        saveProgress(workoutId) { current ->
            current.copy(
                restSeconds = restSeconds.coerceIn(30, 300),
                updatedAtEpochMs = Clock.System.now().toEpochMilliseconds()
            )
        }
    }

    override fun submitFeedback(workoutId: String) {
        saveProgress(workoutId) { current ->
            current.copy(
                submitted = true,
                updatedAtEpochMs = Clock.System.now().toEpochMilliseconds()
            )
        }
    }

    fun close() {
        job.cancel()
    }

    private fun saveProgress(
        workoutId: String,
        transform: (WorkoutProgress) -> WorkoutProgress
    ) {
        val current = mutableState.value.progress[workoutId] ?: WorkoutProgress(workoutId = workoutId)
        scope.launch {
            trainingRepository.saveWorkoutProgress(transform(current))
        }
    }
}

@Composable
actual fun rememberWorkoutPresenter(): WorkoutPresenter {
    val presenter = remember {
        val koin = requireNotNull(KoinProvider.koin) { "Koin is not started" }
        IosWorkoutPresenter(
            trainingRepository = koin.get<TrainingRepository>(),
            logWorkoutSet = koin.get<LogWorkoutSet>(),
            ensureCoachData = koin.get<EnsureCoachData>()
        )
    }
    DisposableEffect(Unit) { onDispose { presenter.close() } }
    return presenter
}

