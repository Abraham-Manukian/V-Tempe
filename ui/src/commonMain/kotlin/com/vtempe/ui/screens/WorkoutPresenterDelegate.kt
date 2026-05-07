package com.vtempe.ui.screens

import com.vtempe.shared.domain.model.PerformedSet
import com.vtempe.shared.domain.model.WorkoutProgress
import com.vtempe.shared.domain.model.WorkoutSet
import com.vtempe.shared.domain.repository.ProfileRepository
import com.vtempe.shared.domain.repository.TrainingRepository
import com.vtempe.shared.domain.usecase.EnsureCoachData
import com.vtempe.shared.domain.usecase.LogWorkoutSet
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock

/**
 * Shared business logic for workout screens — used by both [WorkoutViewModel] (Android)
 * and [IosWorkoutPresenter] (iOS). Each platform supplies its own [CoroutineScope].
 */
class WorkoutPresenterDelegate(
    private val trainingRepository: TrainingRepository,
    private val logWorkoutSet: LogWorkoutSet,
    private val ensureCoachData: EnsureCoachData,
    private val profileRepository: ProfileRepository,
    private val scope: CoroutineScope
) {
    private val _state = MutableStateFlow(WorkoutState())
    val state: StateFlow<WorkoutState> = _state.asStateFlow()

    init {
        scope.launch {
            combine(
                trainingRepository.observeWorkouts(),
                trainingRepository.observeWorkoutProgress()
            ) { workouts, progress -> workouts to progress }
                .collect { (workouts, progress) ->
                    val selected = _state.value.selectedWorkoutId
                        ?.takeIf { id -> workouts.any { it.id == id } }
                        ?: workouts.firstOrNull()?.id
                    _state.value = _state.value.copy(
                        workouts = workouts,
                        selectedWorkoutId = selected,
                        progress = progress
                    )
                }
        }
        scope.launch { runCatching { ensureCoachData() } }
        scope.launch {
            val coachId = profileRepository.getProfile()?.coachTrainerId
            if (!coachId.isNullOrBlank()) {
                _state.value = _state.value.copy(coachTrainerId = coachId)
            }
        }
    }

    fun select(workoutId: String) {
        _state.value = _state.value.copy(selectedWorkoutId = workoutId)
    }

    fun addSet(exerciseId: String, reps: Int, weight: Double?, rpe: Double?) {
        val id = _state.value.selectedWorkoutId ?: return
        scope.launch { logWorkoutSet(id, WorkoutSet(exerciseId, reps, weight, rpe)) }
    }

    fun updatePerformedSet(
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

    fun updateNotes(workoutId: String, notes: String) {
        saveProgress(workoutId) { current ->
            current.copy(
                notes = notes.take(500),
                updatedAtEpochMs = Clock.System.now().toEpochMilliseconds(),
                submitted = false
            )
        }
    }

    fun updateRestSeconds(workoutId: String, restSeconds: Int) {
        saveProgress(workoutId) { current ->
            current.copy(
                restSeconds = restSeconds.coerceIn(30, 300),
                updatedAtEpochMs = Clock.System.now().toEpochMilliseconds()
            )
        }
    }

    fun submitFeedback(workoutId: String) {
        saveProgress(workoutId) { current ->
            current.copy(
                submitted = true,
                updatedAtEpochMs = Clock.System.now().toEpochMilliseconds()
            )
        }
    }

    private fun saveProgress(
        workoutId: String,
        transform: (WorkoutProgress) -> WorkoutProgress
    ) {
        val current = _state.value.progress[workoutId] ?: WorkoutProgress(workoutId = workoutId)
        scope.launch { trainingRepository.saveWorkoutProgress(transform(current)) }
    }
}
