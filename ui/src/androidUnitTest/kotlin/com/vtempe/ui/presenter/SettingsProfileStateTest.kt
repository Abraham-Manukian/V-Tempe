package com.vtempe.ui.presenter

import com.russhwolf.settings.Settings
import com.vtempe.shared.data.repo.SettingsAdviceRepository
import com.vtempe.shared.domain.account.AccountSession
import com.vtempe.shared.domain.model.AiModelMode
import com.vtempe.shared.domain.model.Advice
import com.vtempe.shared.domain.model.Goal
import com.vtempe.shared.domain.model.NutritionPlan
import com.vtempe.shared.domain.model.Profile
import com.vtempe.shared.domain.model.Sex
import com.vtempe.shared.domain.model.TrainingPlan
import com.vtempe.shared.domain.model.Workout
import com.vtempe.shared.domain.model.WorkoutProgress
import com.vtempe.shared.domain.model.WorkoutSet
import com.vtempe.shared.domain.model.WorkoutSummary
import com.vtempe.shared.domain.repository.AiTrainerRepository
import com.vtempe.shared.domain.repository.AnalyticsRepository
import com.vtempe.shared.domain.repository.AuthRepository
import com.vtempe.shared.domain.repository.CoachBundle
import com.vtempe.shared.domain.repository.CoachCacheRepository
import com.vtempe.shared.domain.repository.NutritionRepository
import com.vtempe.shared.domain.repository.PreferencesRepository
import com.vtempe.shared.domain.repository.ProfileRepository
import com.vtempe.shared.domain.repository.TrainingRepository
import com.vtempe.shared.domain.usecase.BootstrapCoachData
import com.vtempe.shared.domain.usecase.EnsureCoachData
import com.vtempe.shared.domain.usecase.RefreshSleepAdvice
import com.vtempe.shared.domain.usecase.SyncAnalyticsProfile
import com.vtempe.shared.domain.util.DataResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import java.io.IOException
import java.lang.reflect.Proxy
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** VT-AUD-063 (a failed save is not shown as saved) and VT-AUD-070 (a failed read is not endless loading). */
class SettingsProfileStateTest {

    private val stored = Profile("p", 30, Sex.MALE, 180, 70.0, Goal.MAINTAIN, 2)

    private class Profiles(var current: Profile?) : ProfileRepository {
        var failReads = false
        var failWrites = false
        override suspend fun getProfile(): Profile? { if (failReads) throw IOException("db"); return current }
        override suspend fun upsertProfile(profile: Profile) { if (failWrites) throw IOException("db"); current = profile }
        override suspend fun clearAll() { current = null }
    }

    private class Plans : TrainingRepository {
        var fail = false
        var rebuilt = 0
        override suspend fun generatePlan(profile: Profile, weekIndex: Int): TrainingPlan {
            if (fail) throw IOException("offline and no safe plan")
            rebuilt++
            return TrainingPlan(weekIndex, emptyList())
        }
        override suspend fun logSet(workoutId: String, set: WorkoutSet) = Unit
        override fun observeWorkouts() = MutableStateFlow(emptyList<Workout>())
        override fun observeWorkoutsByWeek(weekIndex: Int) = observeWorkouts()
        override fun observeWorkoutProgress() = MutableStateFlow(emptyMap<String, WorkoutProgress>())
        override suspend fun saveWorkoutProgress(progress: WorkoutProgress) = Unit
        override suspend fun updateWorkoutProgress(workoutId: String, transform: (WorkoutProgress) -> WorkoutProgress) = Unit
        override suspend fun recentWorkoutSummaries(limit: Int) = emptyList<WorkoutSummary>()
        override suspend fun savePlan(plan: TrainingPlan) = Unit
        override suspend fun hasPlan(weekIndex: Int) = true
        override suspend fun deleteWeeksFrom(weekIndex: Int) = Unit
        val nutrition = object : NutritionRepository {
            override suspend fun generatePlan(profile: Profile, weekIndex: Int) = NutritionPlan(weekIndex, emptyMap(), emptyList())
            override suspend fun savePlan(plan: NutritionPlan) = Unit
            override fun observePlan() = MutableStateFlow<NutritionPlan?>(null)
            override suspend fun hasPlan(weekIndex: Int) = true
            override suspend fun setActiveWeek(weekIndex: Int) = true
            override fun registerActiveWeek(weekIndex: Int) = Unit
            override suspend fun deleteWeeksFrom(weekIndex: Int) = Unit
        }
    }

    private val offlineAi = object : AiTrainerRepository {
        override suspend fun generateTrainingPlan(profile: Profile, weekIndex: Int): DataResult<TrainingPlan> = DataResult.Failure(DataResult.Reason.Network)
        override suspend fun generateNutritionPlan(profile: Profile, weekIndex: Int): DataResult<NutritionPlan> = DataResult.Failure(DataResult.Reason.Network)
        override suspend fun getSleepAdvice(profile: Profile): DataResult<Advice> = DataResult.Failure(DataResult.Reason.Network)
        override suspend fun bootstrap(profile: Profile, weekIndex: Int): DataResult<CoachBundle> = DataResult.Failure(DataResult.Reason.Network)
    }

    private val cache = object : CoachCacheRepository {
        override fun bundleVersion(): Int? = null
        override fun bundleTimestampMillis(): Long? = null
        override fun markBundleFresh(version: Int, timestampMillis: Long) = Unit
        override fun clearAll() = Unit
        override fun planEpochDateMs(): Long? = 0L
        override fun setPlanEpochDate(ms: Long) = Unit
        override fun clearAllAndResetEpoch() = Unit
    }

    private inline fun <reified T> stub(crossinline answer: (String) -> Any?): T =
        Proxy.newProxyInstance(T::class.java.classLoader, arrayOf(T::class.java)) { _, m, _ -> answer(m.name) } as T

    private fun TestScope.presenter(profiles: Profiles, plans: Plans): SettingsPresenterDelegate {
        val settings = stub<Settings> { name -> if (name == "getStringOrNull" || name == "putString") null else error(name) }
        val advice = SettingsAdviceRepository(settings)
        val preferences = stub<PreferencesRepository> { name ->
            when (name) { "getAiModelMode" -> AiModelMode.FREE; "getAnalyticsConsent" -> false; else -> null }
        }
        val bootstrap = BootstrapCoachData(profiles, offlineAi, plans, plans.nutrition, advice, cache)
        val ensure = EnsureCoachData(
            profiles, plans, plans.nutrition, advice, bootstrap, cache, backgroundScope,
            RefreshSleepAdvice(profiles, offlineAi, advice),
        )
        return SettingsPresenterDelegate(
            profiles, preferences, ensure,
            stub<AccountSession> { error("unused") },
            SyncAnalyticsProfile(preferences, stub<AnalyticsRepository> { null }),
            stub<AuthRepository> { name -> if (name == "getAuthState") MutableStateFlow(null) else error(name) },
            backgroundScope,
        )
    }

    @Test
    fun aFailedSaveKeepsTheStoredProfileAndTheEditorOpen() = runTest {
        val profiles = Profiles(stored)
        val presenter = presenter(profiles, Plans())
        runCurrent()
        profiles.failWrites = true
        var closed = false

        presenter.save(stored.copy(weightKg = 81.0, allergies = listOf("peanut"))) { closed = true }
        runCurrent()

        assertFalse(closed, "the editor stays open with the draft")
        assertTrue(presenter.state.value.saveFailed)
        assertEquals(70.0, presenter.state.value.profile?.weightKg, "the screen shows what is actually stored")
        assertFalse(presenter.state.value.saving)

        profiles.failWrites = false
        presenter.save(stored.copy(weightKg = 81.0)) { closed = true }
        runCurrent()
        assertTrue(closed)
        assertFalse(presenter.state.value.saveFailed)
        assertEquals(81.0, profiles.current?.weightKg)
    }

    @Test
    fun aSavedProfileWhosePlansFailedToRebuildSaysSoAndCanRetry() = runTest {
        val plans = Plans().apply { fail = true }
        val presenter = presenter(Profiles(stored), plans)
        runCurrent()
        var closed = false

        presenter.save(stored.copy(weightKg = 75.0)) { closed = true }
        runCurrent()
        assertTrue(closed, "the profile itself is saved")
        assertTrue(presenter.state.value.planUpdateFailed)

        plans.fail = false
        presenter.retryPlanUpdate()
        runCurrent()
        assertFalse(presenter.state.value.planUpdateFailed)
        assertTrue(plans.rebuilt > 0)
    }

    @Test
    fun aFailedReadIsAnErrorWithRetryNotEndlessLoading() = runTest {
        val profiles = Profiles(stored).apply { failReads = true }
        val presenter = presenter(profiles, Plans())
        runCurrent()
        assertEquals(ProfileLoad.FAILED, presenter.state.value.profileLoad)
        assertNull(presenter.state.value.profile)

        profiles.failReads = false
        presenter.refresh()
        runCurrent()
        assertEquals(ProfileLoad.READY, presenter.state.value.profileLoad)
        assertEquals(stored, presenter.state.value.profile)

        profiles.current = null
        presenter.refresh()
        runCurrent()
        assertEquals(ProfileLoad.MISSING, presenter.state.value.profileLoad)
    }
}
