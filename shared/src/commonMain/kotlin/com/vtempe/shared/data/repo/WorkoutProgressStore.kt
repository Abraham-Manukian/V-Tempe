package com.vtempe.shared.data.repo

import com.russhwolf.settings.Settings
import com.vtempe.shared.db.AppDatabase
import com.vtempe.shared.domain.model.ExercisePerformance
import com.vtempe.shared.domain.model.ExtraWorkoutSet
import com.vtempe.shared.domain.model.Workout
import com.vtempe.shared.domain.model.WorkoutProgress
import com.vtempe.shared.domain.model.WorkoutSet
import com.vtempe.shared.domain.model.WorkoutSummary
import com.vtempe.shared.domain.repository.SyncDomain
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.datetime.Clock
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class WorkoutProgressStore(
    private val settings: Settings,
    private val db: AppDatabase,
    /** Fired after every local write, so a signed-in user's progress reaches the backend without
     *  each call site having to remember to trigger sync itself. Resolved lazily via Koin inside
     *  the DI lambda (same pattern as AuthRepository in KoinModule.kt) to avoid a constructor
     *  cycle with SyncRepository, which itself depends on this store for pull/restore. */
    private val onLocalChange: (SyncDomain) -> Unit = {}
) {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val serializer = MapSerializer(String.serializer(), WorkoutProgress.serializer())
    private val cache = MutableStateFlow(load())

    fun observe(): Flow<Map<String, WorkoutProgress>> = cache.asStateFlow()

    fun current(): Map<String, WorkoutProgress> = cache.value

    suspend fun save(progress: WorkoutProgress) {
        val updated = cache.value.toMutableMap()
        updated[progress.workoutId] = progress
        cache.value = updated
        persist(updated)
        onLocalChange(SyncDomain.WORKOUT_PROGRESS)
    }

    suspend fun appendExtraSet(workoutId: String, set: WorkoutSet) {
        val currentProgress = cache.value[workoutId] ?: WorkoutProgress(workoutId = workoutId)
        save(
            currentProgress.copy(
                extraSets = currentProgress.extraSets + ExtraWorkoutSet(
                    exerciseId = set.exerciseId,
                    reps = set.reps,
                    weightKg = set.weightKg,
                    rpe = set.rpe
                ),
                updatedAtEpochMs = nowEpochMillis(),
                submitted = false
            )
        )
    }

    /** Current local state as the raw JSON string already persisted to [settings] — the exact
     *  shape [SyncRepository][com.vtempe.shared.domain.repository.SyncRepository] pushes. */
    fun rawSnapshot(): String? = settings.getStringOrNull(KEY)

    /** Overwrites local state with a snapshot pulled from the server — e.g. after signing in on
     *  a new device. Bypasses [onLocalChange] (this IS the sync system restoring, not a local
     *  edit that needs pushing back out). */
    fun restoreRaw(rawJson: String) {
        settings.putString(KEY, rawJson)
        cache.value = load()
    }

    suspend fun recentSummaries(limit: Int = 6): List<WorkoutSummary> {
        val workoutsById = loadWorkouts().associateBy { it.id }
        return cache.value.values
            .mapNotNull { progress ->
                val workout = workoutsById[progress.workoutId] ?: return@mapNotNull null
                val completedItems = progress.performedSets.count { it.completed }
                if (completedItems == 0 && progress.extraSets.isEmpty() && progress.notes.isBlank()) {
                    return@mapNotNull null
                }

                val totalVolume = progress.performedSets
                    .filter { it.completed }
                    .sumOf { entry ->
                        val planned = workout.sets.getOrNull(entry.setIndex)
                        val reps = entry.actualReps ?: planned?.reps ?: 0
                        val weight = entry.actualWeightKg ?: planned?.weightKg ?: 0.0
                        reps * weight
                    } + progress.extraSets.sumOf { extra ->
                    extra.reps * (extra.weightKg ?: 0.0)
                }

                val rpeValues = buildList {
                    progress.performedSets.filter { it.completed }.forEach { entry ->
                        val planned = workout.sets.getOrNull(entry.setIndex)
                        val rpe = entry.actualRpe ?: planned?.rpe
                        if (rpe != null) add(rpe)
                    }
                    progress.extraSets.forEach { extra ->
                        val rpe = extra.rpe
                        if (rpe != null) add(rpe)
                    }
                }

                val exercisePerformances = progress.performedSets
                    .filter { it.completed }
                    .mapNotNull { entry ->
                        val planned = workout.sets.getOrNull(entry.setIndex) ?: return@mapNotNull null
                        ExercisePerformance(
                            exerciseId = planned.exerciseId,
                            weightKg = entry.actualWeightKg ?: planned.weightKg,
                            reps = entry.actualReps ?: planned.reps,
                        )
                    }
                    .distinctBy { it.exerciseId }

                WorkoutSummary(
                    workoutId = workout.id,
                    date = workout.date.toString(),
                    completionRate = if (workout.sets.isEmpty()) 0.0 else completedItems.toDouble() / workout.sets.size.toDouble(),
                    completedItems = completedItems,
                    plannedItems = workout.sets.size,
                    totalVolumeKg = totalVolume,
                    averageRpe = rpeValues.takeIf { it.isNotEmpty() }?.average(),
                    notes = progress.notes.trim(),
                    exercises = exercisePerformances,
                )
            }
            .sortedByDescending { it.date }
            .take(limit)
    }

    private fun load(): Map<String, WorkoutProgress> =
        settings.getStringOrNull(KEY)
            ?.let { raw -> runCatching { json.decodeFromString(serializer, raw) }.getOrDefault(emptyMap()) }
            ?: emptyMap()

    private fun persist(progress: Map<String, WorkoutProgress>) {
        settings.putString(KEY, json.encodeToString(serializer, progress))
    }

    private fun loadWorkouts(): List<Workout> =
        db.workoutQueries.selectWorkoutsWithSets().executeAsList().toWorkoutDomainList()

    private fun nowEpochMillis(): Long = Clock.System.now().toEpochMilliseconds()

    private companion object {
        const val KEY = "workout.progress.v1"
    }
}
