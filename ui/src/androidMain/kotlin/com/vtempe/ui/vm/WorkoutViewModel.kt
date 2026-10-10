package com.vtempe.ui.vm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vtempe.shared.domain.repository.CoachCacheRepository
import com.vtempe.shared.domain.repository.ProfileRepository
import com.vtempe.shared.domain.repository.TrainingRepository
import com.vtempe.shared.domain.usecase.EnsureCoachData
import com.vtempe.shared.domain.usecase.LogWorkoutSet
import com.vtempe.ui.presenter.WorkoutPresenter
import com.vtempe.ui.presenter.WorkoutPresenterDelegate
import com.vtempe.ui.presenter.WorkoutState
import kotlinx.coroutines.flow.StateFlow

class WorkoutViewModel(
    trainingRepository: TrainingRepository,
    logWorkoutSet: LogWorkoutSet,
    ensureCoachData: EnsureCoachData,
    profileRepository: ProfileRepository,
    coachCache: CoachCacheRepository,
) : ViewModel(), WorkoutPresenter {

    private val delegate = WorkoutPresenterDelegate(
        trainingRepository = trainingRepository,
        logWorkoutSet = logWorkoutSet,
        ensureCoachData = ensureCoachData,
        profileRepository = profileRepository,
        coachCache = coachCache,
        scope = viewModelScope
    )

    override val state: StateFlow<WorkoutState> = delegate.state
    override fun refresh() = delegate.refresh()

    override fun select(workoutId: String) = delegate.select(workoutId)
    override fun openWorkout(workoutId: String) = delegate.openWorkout(workoutId)
    override fun closeWorkout() = delegate.closeWorkout()
    override fun startRest(seconds: Int) = delegate.startRest(seconds)
    override fun skipRest() = delegate.skipRest()

    override fun addSet(exerciseId: String, reps: Int, weight: Double?, rpe: Double?) =
        delegate.addSet(exerciseId, reps, weight, rpe)

    override fun markSetDone(
        workoutId: String,
        exerciseIndex: Int,
        actualReps: Int?,
        actualWeightKg: Double?,
        actualRpe: Double?
    ) = delegate.markSetDone(workoutId, exerciseIndex, actualReps, actualWeightKg, actualRpe)

    override fun undoSetDone(workoutId: String, exerciseIndex: Int) = delegate.undoSetDone(workoutId, exerciseIndex)

    override fun updateNotes(workoutId: String, notes: String) =
        delegate.updateNotes(workoutId, notes)

    override fun updateRestSeconds(workoutId: String, restSeconds: Int) =
        delegate.updateRestSeconds(workoutId, restSeconds)

    override fun submitFeedback(workoutId: String) =
        delegate.submitFeedback(workoutId)
}
