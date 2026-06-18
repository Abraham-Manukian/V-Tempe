package com.vtempe.ui.presenter

import androidx.compose.runtime.Immutable
import com.vtempe.shared.domain.model.CoachTrainerIds
import com.vtempe.shared.domain.model.ExtraWorkoutSet
import com.vtempe.shared.domain.model.PerformedSet
import com.vtempe.shared.domain.model.Workout
import com.vtempe.shared.domain.model.WorkoutProgress
import com.vtempe.shared.domain.model.WorkoutSet
import com.vtempe.shared.domain.repository.CoachCacheRepository
import com.vtempe.shared.domain.repository.ProfileRepository
import com.vtempe.shared.domain.repository.TrainingRepository
import com.vtempe.shared.domain.usecase.EnsureCoachData
import com.vtempe.shared.domain.util.CoachSchedule
import com.vtempe.shared.domain.usecase.LogWorkoutSet
import io.github.aakira.napier.Napier
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock

const val NOTES_MAX_CHARS = 500
const val REST_SECONDS_MIN = 30
const val REST_SECONDS_MAX = 300

@Immutable
data class WorkoutState(
    val workouts: List<Workout> = emptyList(),
    val selectedWorkoutId: String? = null,
    val progress: Map<String, WorkoutProgress> = emptyMap(),
    val coachTrainerId: String = CoachTrainerIds.DEFAULT
)

interface WorkoutPresenter {
    val state: StateFlow<WorkoutState>
    fun refresh()
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
    /** Increments completedSetsCount for the exercise. Marks exercise done when count reaches totalSets. */
    fun markSetDone(
        workoutId: String,
        exerciseIndex: Int,
        totalSets: Int,
        actualReps: Int?,
        actualWeightKg: Double?,
        actualRpe: Double?
    )
    fun updateNotes(workoutId: String, notes: String)
    fun updateRestSeconds(workoutId: String, restSeconds: Int)
    fun submitFeedback(workoutId: String)
}

class WorkoutPresenterDelegate(
    private val trainingRepository: TrainingRepository,
    private val logWorkoutSet: LogWorkoutSet,
    private val ensureCoachData: EnsureCoachData,
    private val profileRepository: ProfileRepository,
    private val coachCache: CoachCacheRepository,
    private val scope: CoroutineScope,
) : WorkoutPresenter {

    private val _state = MutableStateFlow(WorkoutState())
    override val state: StateFlow<WorkoutState> = _state.asStateFlow()

    // Mutable so refresh() can switch to the new week without recreating the presenter.
    private val weekIndexFlow = MutableStateFlow(
        CoachSchedule.currentWeekIndex(coachCache.planEpochDateMs())
    )

    init {
        // flatMapLatest re-subscribes automatically whenever weekIndexFlow emits a new value,
        // so navigating to this screen on a new week shows the correct workouts.
        weekIndexFlow
            .flatMapLatest { weekIndex ->
                combine(
                    trainingRepository.observeWorkoutsByWeek(weekIndex),
                    trainingRepository.observeWorkoutProgress()
                ) { workouts, progress -> workouts to progress }
            }
            .onEach { (workouts, progress) ->
                _state.update { it.copy(workouts = workouts, progress = progress) }
            }
            .catch { Napier.e("WorkoutPresenter observe error", it) }
            .launchIn(scope)

        scope.launch {
            val profile = runCatching { profileRepository.getProfile() }.getOrNull()
            if (profile != null) {
                _state.update { it.copy(coachTrainerId = profile.coachTrainerId) }
            }
            runCatching { ensureCoachData() }
                .onFailure { Napier.w("EnsureCoachData failed on Workout", it) }
        }
    }

    override fun refresh() {
        val newWeek = CoachSchedule.currentWeekIndex(coachCache.planEpochDateMs())
        weekIndexFlow.value = newWeek
        scope.launch {
            runCatching { ensureCoachData() }
                .onFailure { Napier.w("EnsureCoachData failed on Workout refresh", it) }
        }
    }

    override fun select(workoutId: String) {
        _state.update { it.copy(selectedWorkoutId = workoutId) }
    }

    override fun addSet(exerciseId: String, reps: Int, weight: Double?, rpe: Double?) {
        val workoutId = _state.value.selectedWorkoutId ?: return
        scope.launch {
            runCatching { logWorkoutSet(workoutId, WorkoutSet(exerciseId, reps, weight, rpe)) }
                .onFailure { Napier.e("addSet failed", it) }
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
        scope.launch {
            val current = _state.value.progress[workoutId] ?: WorkoutProgress(workoutId)
            val sets = current.performedSets.toMutableList()
            val idx = sets.indexOfFirst { it.setIndex == setIndex }
            val existing = sets.getOrNull(idx)
            val updated = PerformedSet(setIndex, completed, existing?.completedSetsCount ?: 0, actualReps, actualWeightKg, actualRpe)
            if (idx >= 0) sets[idx] = updated else sets.add(updated)
            val newProgress = current.copy(
                performedSets = sets,
                updatedAtEpochMs = Clock.System.now().toEpochMilliseconds()
            )
            runCatching { trainingRepository.saveWorkoutProgress(newProgress) }
                .onFailure { Napier.e("updatePerformedSet failed", it) }
        }
    }

    override fun markSetDone(
        workoutId: String,
        exerciseIndex: Int,
        totalSets: Int,
        actualReps: Int?,
        actualWeightKg: Double?,
        actualRpe: Double?
    ) {
        scope.launch {
            val current = _state.value.progress[workoutId] ?: WorkoutProgress(workoutId)
            val sets = current.performedSets.toMutableList()
            val idx = sets.indexOfFirst { it.setIndex == exerciseIndex }
            val existing = sets.getOrNull(idx)
            val newCount = (existing?.completedSetsCount ?: 0) + 1
            val isComplete = newCount >= totalSets
            val updated = PerformedSet(
                setIndex = exerciseIndex,
                completed = isComplete,
                completedSetsCount = newCount,
                actualReps = actualReps,
                actualWeightKg = actualWeightKg,
                actualRpe = actualRpe
            )
            if (idx >= 0) sets[idx] = updated else sets.add(updated)
            val newProgress = current.copy(
                performedSets = sets,
                updatedAtEpochMs = Clock.System.now().toEpochMilliseconds()
            )
            runCatching { trainingRepository.saveWorkoutProgress(newProgress) }
                .onFailure { Napier.e("markSetDone failed", it) }
        }
    }

    override fun updateNotes(workoutId: String, notes: String) {
        scope.launch {
            val current = _state.value.progress[workoutId] ?: WorkoutProgress(workoutId)
            val newProgress = current.copy(
                notes = notes.take(NOTES_MAX_CHARS),
                updatedAtEpochMs = Clock.System.now().toEpochMilliseconds()
            )
            runCatching { trainingRepository.saveWorkoutProgress(newProgress) }
                .onFailure { Napier.e("updateNotes failed", it) }
        }
    }

    override fun updateRestSeconds(workoutId: String, restSeconds: Int) {
        scope.launch {
            val current = _state.value.progress[workoutId] ?: WorkoutProgress(workoutId)
            val clamped = restSeconds.coerceIn(REST_SECONDS_MIN, REST_SECONDS_MAX)
            val newProgress = current.copy(
                restSeconds = clamped,
                updatedAtEpochMs = Clock.System.now().toEpochMilliseconds()
            )
            runCatching { trainingRepository.saveWorkoutProgress(newProgress) }
                .onFailure { Napier.e("updateRestSeconds failed", it) }
        }
    }

    override fun submitFeedback(workoutId: String) {
        scope.launch {
            val current = _state.value.progress[workoutId] ?: WorkoutProgress(workoutId)
            val newProgress = current.copy(
                submitted = true,
                updatedAtEpochMs = Clock.System.now().toEpochMilliseconds()
            )
            runCatching { trainingRepository.saveWorkoutProgress(newProgress) }
                .onFailure { Napier.e("submitFeedback failed", it) }
        }
    }
}
