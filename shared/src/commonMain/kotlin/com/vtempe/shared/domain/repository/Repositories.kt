package com.vtempe.shared.domain.repository

import com.vtempe.shared.domain.model.*
import com.vtempe.shared.domain.util.DataResult
import kotlinx.coroutines.flow.Flow

interface ProfileRepository {
    suspend fun getProfile(): Profile?
    suspend fun upsertProfile(profile: Profile)
    suspend fun clearAll()
}

interface TrainingRepository {
    suspend fun generatePlan(profile: Profile, weekIndex: Int): TrainingPlan
    suspend fun logSet(workoutId: String, set: WorkoutSet)
    /** All workouts — for history / progress screens. */
    fun observeWorkouts(): Flow<List<Workout>>
    /** Only workouts for the given week — for the active workout screen. */
    fun observeWorkoutsByWeek(weekIndex: Int): Flow<List<Workout>>
    fun observeWorkoutProgress(): Flow<Map<String, WorkoutProgress>>
    suspend fun saveWorkoutProgress(progress: WorkoutProgress)
    suspend fun recentWorkoutSummaries(limit: Int = 6): List<WorkoutSummary>
    suspend fun savePlan(plan: TrainingPlan)
    suspend fun hasPlan(weekIndex: Int): Boolean
    /** Deletes plan workouts for all weeks >= weekIndex. Called on force-refresh (profile change). */
    suspend fun deleteWeeksFrom(weekIndex: Int)
}

interface NutritionRepository {
    suspend fun generatePlan(profile: Profile, weekIndex: Int): NutritionPlan
    suspend fun savePlan(plan: NutritionPlan)
    fun observePlan(): Flow<NutritionPlan?>
    /** Pure DB check — no side effects on the observable flow. */
    suspend fun hasPlan(weekIndex: Int): Boolean
    /**
     * Marks [weekIndex] as the week currently on screen.
     * Loads that week's plan from DB into the observable flow so the UI renders immediately.
     * Returns true if a cached plan was found.
     */
    suspend fun setActiveWeek(weekIndex: Int): Boolean
    /**
     * Registers the active week WITHOUT loading from DB (no IO, instant).
     * Call this before a force-refresh so [savePlan] knows to update the flow
     * once the new AI plan arrives, but without showing stale cached data first.
     */
    fun registerActiveWeek(weekIndex: Int)
    /** Deletes meal plans for all weeks >= weekIndex. Called on force-refresh. */
    suspend fun deleteWeeksFrom(weekIndex: Int)
}

interface AdviceRepository {
    suspend fun getAdvice(profile: Profile, context: Map<String, Any?>): Advice
    suspend fun saveAdvice(topic: String, advice: Advice)
    fun observeAdvice(topic: String): Flow<Advice>
    suspend fun hasAdvice(topic: String): Boolean
}

interface PurchasesRepository {
    suspend fun isSubscriptionActive(): Boolean
}

interface SyncRepository {
    suspend fun syncAll(): Boolean
}

/** Narrow interface for classes that only need locale access. */
interface LanguagePreferences {
    fun getLanguageTag(): String?
    fun setLanguageTag(tag: String?)
}

/** Narrow interface for classes that only need AI model selection. */
interface AiModelPreferences {
    fun getAiModelMode(): AiModelMode
    fun setAiModelMode(mode: AiModelMode)
}

/** Narrow interface for classes that only need measurement units. */
interface UnitPreferences {
    fun getUnits(): String?
    fun setUnits(units: String?)
}

/** Combined interface used by Settings and Onboarding screens. */
interface PreferencesRepository : LanguagePreferences, AiModelPreferences, UnitPreferences

interface ExerciseCalibrationRepository {
    suspend fun get(exerciseId: String): com.vtempe.shared.domain.exercise.ExerciseCalibrationRecord?
    suspend fun list(): List<com.vtempe.shared.domain.exercise.ExerciseCalibrationRecord>
    suspend fun upsert(record: com.vtempe.shared.domain.exercise.ExerciseCalibrationRecord)
    suspend fun clear(exerciseId: String)
    suspend fun clearAll()
}

interface AiTrainerRepository {
    suspend fun generateTrainingPlan(profile: Profile, weekIndex: Int): DataResult<TrainingPlan>
    suspend fun generateNutritionPlan(profile: Profile, weekIndex: Int): DataResult<NutritionPlan>
    suspend fun getSleepAdvice(profile: Profile): DataResult<Advice>
    suspend fun bootstrap(profile: Profile, weekIndex: Int): DataResult<CoachBundle>
}

data class ChatMessage(val role: String, val content: String)

data class CoachBundle(
    val trainingPlan: TrainingPlan? = null,
    val nutritionPlan: NutritionPlan? = null,
    val sleepAdvice: Advice? = null
)

data class CoachResponse(
    val reply: String,
    val trainingPlan: TrainingPlan? = null,
    val nutritionPlan: NutritionPlan? = null,
    val sleepAdvice: Advice? = null,
    val actions: List<CoachAction> = emptyList()
)

interface ChatRepository {
    suspend fun send(
        profile: Profile,
        history: List<ChatMessage>,
        userMessage: String,
        locale: String?,
        currentTrainingPlan: TrainingPlan? = null,
        currentNutritionPlan: NutritionPlan? = null
    ): DataResult<CoachResponse>
}

/**
 * Domain-layer port for tracking the freshness of AI-generated coach data.
 * Implemented by the data layer; domain use cases depend only on this interface.
 */
interface CoachCacheRepository {
    fun bundleVersion(): Int?
    fun bundleTimestampMillis(): Long?
    fun markBundleFresh(version: Int, timestampMillis: Long)
    fun clearAll()

    /** Epoch = timestamp of the very first successful bootstrap. Never changes after set. */
    fun planEpochDateMs(): Long?
    fun setPlanEpochDate(ms: Long)

    /**
     * Clears the epoch date (and all other cache).
     * Call ONLY on full reset / re-registration so the week counter restarts from zero.
     * Regular cache clears (schema upgrades etc.) should NOT reset the epoch.
     */
    fun clearAllAndResetEpoch()
}







