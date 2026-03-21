package com.vtempe.ui.vm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vtempe.shared.domain.model.PerformedSet
import com.vtempe.shared.domain.model.WorkoutProgress
import com.vtempe.shared.domain.model.WorkoutSet
import com.vtempe.shared.domain.repository.TrainingRepository
import com.vtempe.shared.domain.usecase.EnsureCoachData
import com.vtempe.shared.domain.usecase.LogWorkoutSet
import com.vtempe.ui.screens.WorkoutPresenter
import com.vtempe.ui.screens.WorkoutState
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock

class WorkoutViewModel(
    private val trainingRepository: TrainingRepository,
    private val logWorkoutSet: LogWorkoutSet,
    private val ensureCoachData: EnsureCoachData,
) : ViewModel(), WorkoutPresenter {
    private val _state = MutableStateFlow(WorkoutState())
    override val state: StateFlow<WorkoutState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            combine(
                trainingRepository.observeWorkouts(),
                trainingRepository.observeWorkoutProgress()
            ) { workouts, progress ->
                workouts to progress
            }.collect { (workouts, progress) ->
                val selected = _state.value.selectedWorkoutId
                    ?.takeIf { currentId -> workouts.any { it.id == currentId } }
                    ?: workouts.firstOrNull()?.id
                _state.value = _state.value.copy(
                    workouts = workouts,
                    selectedWorkoutId = selected,
                    progress = progress
                )
            }
        }
        viewModelScope.launch { runCatching { ensureCoachData() } }
    }

    override fun select(workoutId: String) { _state.value = _state.value.copy(selectedWorkoutId = workoutId) }

    override fun addSet(exerciseId: String, reps: Int, weight: Double?, rpe: Double?) {
        val id = _state.value.selectedWorkoutId ?: return
        viewModelScope.launch { logWorkoutSet(id, WorkoutSet(exerciseId, reps, weight, rpe)) }
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

    override fun updateNotes(workoutId: String, notes: String) {
        saveProgress(workoutId) { current ->
            current.copy(
                notes = notes.take(500),
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

    private fun saveProgress(
        workoutId: String,
        transform: (WorkoutProgress) -> WorkoutProgress
    ) {
        val current = _state.value.progress[workoutId] ?: WorkoutProgress(workoutId = workoutId)
        viewModelScope.launch {
            trainingRepository.saveWorkoutProgress(transform(current))
        }
    }
}

