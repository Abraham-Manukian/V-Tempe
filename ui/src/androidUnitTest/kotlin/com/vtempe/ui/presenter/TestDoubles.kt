package com.vtempe.ui.presenter

import com.russhwolf.settings.Settings
import com.vtempe.shared.domain.model.NutritionPlan
import com.vtempe.shared.domain.model.Profile
import com.vtempe.shared.domain.model.TrainingPlan
import com.vtempe.shared.domain.model.Workout
import com.vtempe.shared.domain.model.WorkoutProgress
import com.vtempe.shared.domain.model.WorkoutSet
import com.vtempe.shared.domain.model.WorkoutSummary
import com.vtempe.shared.domain.repository.CoachCacheRepository
import com.vtempe.shared.domain.repository.NutritionRepository
import com.vtempe.shared.domain.repository.TrainingRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import java.lang.reflect.Proxy

/** An interface implementation that answers every call through [answer] (method name → result). */
internal inline fun <reified T> stub(crossinline answer: (String) -> Any?): T =
    Proxy.newProxyInstance(T::class.java.classLoader, arrayOf(T::class.java)) { _, m, _ -> answer(m.name) } as T

/** String-only Settings backed by a map — what the stores under test use. */
internal fun memorySettings(values: MutableMap<String, String> = mutableMapOf()): Settings =
    Proxy.newProxyInstance(Settings::class.java.classLoader, arrayOf(Settings::class.java)) { _, method, args ->
        when (method.name) {
            "getStringOrNull" -> values[args!![0] as String]
            "putString" -> { values[args!![0] as String] = args[1] as String; null }
            "remove" -> { values.remove(args!![0] as String); null }
            else -> error("Unexpected Settings call: ${method.name}")
        }
    } as Settings

internal open class FakeTraining(private val workouts: List<Workout> = emptyList()) : TrainingRepository {
    override suspend fun generatePlan(profile: Profile, weekIndex: Int) = TrainingPlan(weekIndex, emptyList())
    override suspend fun logSet(workoutId: String, set: WorkoutSet) = Unit
    override fun observeWorkouts(): Flow<List<Workout>> = MutableStateFlow(workouts)
    override fun observeWorkoutsByWeek(weekIndex: Int) = observeWorkouts()
    override fun observeWorkoutProgress() = MutableStateFlow(emptyMap<String, WorkoutProgress>())
    override suspend fun saveWorkoutProgress(progress: WorkoutProgress) = Unit
    override suspend fun updateWorkoutProgress(workoutId: String, transform: (WorkoutProgress) -> WorkoutProgress) = Unit
    override suspend fun recentWorkoutSummaries(limit: Int) = emptyList<WorkoutSummary>()
    override suspend fun savePlan(plan: TrainingPlan) = Unit
    override suspend fun hasPlan(weekIndex: Int) = true
    override suspend fun deleteWeeksFrom(weekIndex: Int) = Unit
}

internal open class FakeNutrition(private val plan: NutritionPlan? = null) : NutritionRepository {
    override suspend fun generatePlan(profile: Profile, weekIndex: Int) = NutritionPlan(weekIndex, emptyMap(), emptyList())
    override suspend fun savePlan(plan: NutritionPlan) = Unit
    override fun observePlan() = MutableStateFlow(plan)
    override suspend fun hasPlan(weekIndex: Int) = true
    override suspend fun setActiveWeek(weekIndex: Int) = true
    override fun registerActiveWeek(weekIndex: Int) = Unit
    override suspend fun deleteWeeksFrom(weekIndex: Int) = Unit
}

internal class FakeCoachCache(private val epochMs: Long? = 0L) : CoachCacheRepository {
    override fun bundleVersion(): Int? = null
    override fun bundleTimestampMillis(): Long? = null
    override fun markBundleFresh(version: Int, timestampMillis: Long) = Unit
    override fun clearAll() = Unit
    override fun planEpochDateMs(): Long? = epochMs
    override fun setPlanEpochDate(ms: Long) = Unit
    override fun clearAllAndResetEpoch() = Unit
}
