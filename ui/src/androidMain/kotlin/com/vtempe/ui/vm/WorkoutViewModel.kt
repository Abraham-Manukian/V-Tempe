package com.vtempe.ui.vm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vtempe.shared.domain.repository.ProfileRepository
import com.vtempe.shared.domain.repository.TrainingRepository
import com.vtempe.shared.domain.usecase.EnsureCoachData
import com.vtempe.shared.domain.usecase.LogWorkoutSet
import com.vtempe.ui.screens.WorkoutPresenter
import com.vtempe.ui.screens.WorkoutPresenterDelegate
import com.vtempe.ui.screens.WorkoutState
import kotlinx.coroutines.flow.StateFlow

class WorkoutViewModel(
    trainingRepository: TrainingRepository,
    logWorkoutSet: LogWorkoutSet,
    ensureCoachData: EnsureCoachData,
    profileRepository: ProfileRepository,
) : ViewModel(), WorkoutPresenter {

    private val delegate = WorkoutPresenterDelegate(
        trainingRepository = trainingRepository,
        logWorkoutSet = logWorkoutSet,
        ensureCoachData = ensureCoachData,
        profileRepository = profileRepository,
        scope = viewModelScope
    )

    override val state: StateFlow<WorkoutState> = delegate.state

    override fun select(workoutId: String) = delegate.select(workoutId)

    override fun addSet(exerciseId: String, reps: Int, weight: Double?, rpe: Double?) =
        delegate.addSet(exerciseId, reps, weight, rpe)

    override fun updatePerformedSet(
        workoutId: String,
        setIndex: Int,
        completed: Boolean,
        actualReps: Int?,
        actualWeightKg: Double?,
        actualRpe: Double?
    ) = delegate.updatePerformedSet(workoutId, setIndex, completed, actualReps, actualWeightKg, actualRpe)

    override fun updateNotes(workoutId: String, notes: String) =
        delegate.updateNotes(workoutId, notes)

    override fun updateRestSeconds(workoutId: String, restSeconds: Int) =
        delegate.updateRestSeconds(workoutId, restSeconds)

    override fun submitFeedback(workoutId: String) =
        delegate.submitFeedback(workoutId)
}
