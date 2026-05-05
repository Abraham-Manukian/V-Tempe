package com.vtempe.ui.screens

import androidx.compose.runtime.Composable
import com.vtempe.shared.domain.model.CoachTrainerIds
import com.vtempe.shared.domain.model.Workout
import com.vtempe.shared.domain.model.WorkoutProgress
import kotlinx.coroutines.flow.StateFlow

data class WorkoutState(
    val workouts: List<Workout> = emptyList(),
    val selectedWorkoutId: String? = null,
    val progress: Map<String, WorkoutProgress> = emptyMap(),
    val coachTrainerId: String = CoachTrainerIds.DEFAULT
)

interface WorkoutPresenter {
    val state: StateFlow<WorkoutState>
    fun select(workoutId: String)
    fun addSet(exerciseId: String, reps: Int, weight: Double?, rpe: Double?)
    fun updatePerformedSet(
        workoutId: String,
        setIndex: Int,
        completed: Boolean,
        actualReps: Int?,
        actualWeightKg: Double?,
        actualRpe: Double?
    )
    fun updateNotes(workoutId: String, notes: String)
    fun updateRestSeconds(workoutId: String, restSeconds: Int)
    fun submitFeedback(workoutId: String)
}

@Composable
expect fun rememberWorkoutPresenter(): WorkoutPresenter

