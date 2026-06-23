package com.vtempe.ui.screens

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import com.vtempe.shared.data.di.KoinProvider
import com.vtempe.shared.domain.repository.CoachCacheRepository
import com.vtempe.shared.domain.repository.ProfileRepository
import com.vtempe.shared.domain.repository.TrainingRepository
import com.vtempe.shared.domain.usecase.EnsureCoachData
import com.vtempe.shared.domain.usecase.LogWorkoutSet
import com.vtempe.ui.presenter.WorkoutPresenter
import com.vtempe.ui.presenter.WorkoutPresenterDelegate
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

private class IosWorkoutPresenter(
    trainingRepository: TrainingRepository,
    logWorkoutSet: LogWorkoutSet,
    ensureCoachData: EnsureCoachData,
    profileRepository: ProfileRepository,
    coachCache: CoachCacheRepository,
) : WorkoutPresenter {
    private val job = SupervisorJob()
    private val scope = CoroutineScope(Dispatchers.Main + job)
    private val delegate = WorkoutPresenterDelegate(
        trainingRepository = trainingRepository,
        logWorkoutSet = logWorkoutSet,
        ensureCoachData = ensureCoachData,
        profileRepository = profileRepository,
        coachCache = coachCache,
        scope = scope
    )
    override val state get() = delegate.state
    override fun refresh() = delegate.refresh()
    override fun select(workoutId: String) = delegate.select(workoutId)
    override fun addSet(exerciseId: String, reps: Int, weight: Double?, rpe: Double?) = delegate.addSet(exerciseId, reps, weight, rpe)
    override fun updatePerformedSet(workoutId: String, setIndex: Int, completed: Boolean, actualReps: Int?, actualWeightKg: Double?, actualRpe: Double?) =
        delegate.updatePerformedSet(workoutId, setIndex, completed, actualReps, actualWeightKg, actualRpe)
    override fun markSetDone(workoutId: String, exerciseIndex: Int, totalSets: Int, actualReps: Int?, actualWeightKg: Double?, actualRpe: Double?) =
        delegate.markSetDone(workoutId, exerciseIndex, totalSets, actualReps, actualWeightKg, actualRpe)
    override fun updateNotes(workoutId: String, notes: String) = delegate.updateNotes(workoutId, notes)
    override fun updateRestSeconds(workoutId: String, restSeconds: Int) = delegate.updateRestSeconds(workoutId, restSeconds)
    override fun submitFeedback(workoutId: String) = delegate.submitFeedback(workoutId)
    fun close() = job.cancel()
}

@Composable
actual fun rememberWorkoutPresenter(): WorkoutPresenter {
    val presenter = remember {
        val koin = requireNotNull(KoinProvider.koin) { "Koin is not started" }
        IosWorkoutPresenter(
            trainingRepository = koin.get(),
            logWorkoutSet = koin.get(),
            ensureCoachData = koin.get(),
            profileRepository = koin.get(),
            coachCache = koin.get()
        )
    }
    DisposableEffect(Unit) { onDispose { presenter.close() } }
    return presenter
}
