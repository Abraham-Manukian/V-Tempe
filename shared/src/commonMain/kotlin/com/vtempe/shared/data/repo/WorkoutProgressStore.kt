package com.vtempe.shared.data.repo

import com.russhwolf.settings.Settings
import com.vtempe.shared.db.AppDatabase
import com.vtempe.shared.domain.model.ExtraWorkoutSet
import com.vtempe.shared.domain.model.Workout
import com.vtempe.shared.domain.model.WorkoutProgress
import com.vtempe.shared.domain.model.WorkoutSet
import com.vtempe.shared.domain.model.WorkoutSummary
import com.vtempe.shared.domain.repository.SyncDomain
import com.vtempe.shared.domain.training.WorkoutHistory
import com.vtempe.shared.domain.training.WorkoutProgressEditor
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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
    /** Serializes read-modify-write so two quick taps can never both start from the same state. */
    private val writeLock = Mutex()

    fun observe(): Flow<Map<String, WorkoutProgress>> = cache.asStateFlow()

    fun current(): Map<String, WorkoutProgress> = cache.value

    suspend fun save(progress: WorkoutProgress) = writeLock.withLock { write(progress) }

    /** Atomically applies [transform] to the latest stored progress of [workoutId]. */
    suspend fun update(workoutId: String, transform: (WorkoutProgress) -> WorkoutProgress) = writeLock.withLock {
        val current = cache.value[workoutId] ?: WorkoutProgress(workoutId = workoutId)
        val updated = transform(current)
        if (updated != current) write(updated.copy(updatedAtEpochMs = nowEpochMillis()))
    }

    /**
     * Before [workouts] are overwritten by a new plan version, ties their legacy progress records
     * (written before records carried an exercise id) to the exercises they were done for, so
     * the new version cannot inherit them by position (VT-AUD-020).
     */
    suspend fun bindLegacyRecords(workouts: List<Workout>) = writeLock.withLock {
        workouts.forEach { workout ->
            val current = cache.value[workout.id] ?: return@forEach
            val bound = WorkoutProgressEditor.bindLegacyRecords(current, workout)
            if (bound != current) write(bound)
        }
    }

    /** Stored first, published second (VT-AUD-062): a failed write must not look saved on screen. */
    private fun write(progress: WorkoutProgress) {
        val updated = cache.value + (progress.workoutId to progress)
        persist(updated)
        cache.value = updated
        onLocalChange(SyncDomain.WORKOUT_PROGRESS)
    }

    suspend fun appendExtraSet(workoutId: String, set: WorkoutSet) = update(workoutId) { currentProgress ->
        currentProgress.copy(
            extraSets = currentProgress.extraSets + ExtraWorkoutSet(
                exerciseId = set.exerciseId,
                reps = set.reps,
                weightKg = set.weightKg,
                rpe = set.rpe
            ),
            submitted = false
        )
    }

    /** Current local state as the raw JSON string already persisted to [settings] — the exact
     *  shape [SyncRepository][com.vtempe.shared.domain.repository.SyncRepository] pushes. */
    fun rawSnapshot(): String? = settings.getStringOrNull(KEY)

    /** Overwrites local state with a snapshot pulled from the server — e.g. after signing in on
     *  a new device. Bypasses [onLocalChange] (this IS the sync system restoring, not a local
     *  edit that needs pushing back out). */
    fun isReadableSnapshot(rawJson: String): Boolean = decode(rawJson) != null

    fun restoreRaw(rawJson: String): Boolean {
        // Decoded first (VT-AUD-049): an unreadable snapshot must not replace readable progress.
        val decoded = decode(rawJson) ?: return false
        persist(decoded)
        cache.value = decoded
        return true
    }

    fun clear() {
        settings.remove(KEY)
        cache.value = emptyMap()
    }

    suspend fun recentSummaries(limit: Int = 6): List<WorkoutSummary> {
        val workoutsById = loadWorkouts().associateBy { it.id }
        return cache.value.values
            .mapNotNull { progress ->
                val workout = workoutsById[progress.workoutId] ?: return@mapNotNull null
                WorkoutHistory.summarize(workout, progress)
            }
            .sortedByDescending { it.date }
            .take(limit)
    }

    private fun load(): Map<String, WorkoutProgress> = settings.getStringOrNull(KEY)?.let(::decode).orEmpty()

    private fun decode(raw: String): Map<String, WorkoutProgress>? =
        runCatching { json.decodeFromString(serializer, raw) }.getOrNull()

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
