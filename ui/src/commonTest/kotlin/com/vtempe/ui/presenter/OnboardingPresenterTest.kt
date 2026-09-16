package com.vtempe.ui.presenter

import com.vtempe.shared.domain.model.*
import com.vtempe.shared.domain.repository.*
import com.vtempe.shared.domain.usecase.BootstrapCoachData
import com.vtempe.shared.domain.usecase.SyncAnalyticsProfile
import com.vtempe.shared.domain.util.DataResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.*

@OptIn(ExperimentalCoroutinesApi::class)
class OnboardingPresenterTest {
    @Test
    fun successPreservesInputAndCompletesOnce() = runTest {
        val fixture = Fixture()
        val presenter = fixture.presenter(backgroundScope)
        presenter.update { it.copy(injuries = "knee, shoulder", weightKg = "81.5") }
        var completed = 0
        presenter.save { completed++ }
        runCurrent()
        assertEquals(1, completed)
        assertEquals(listOf("knee", "shoulder"), fixture.profile?.constraints?.injuries)
        assertEquals(81.5, fixture.profile?.weightKg)
        assertTrue(fixture.trainingReady && fixture.nutritionReady && fixture.adviceReady)
        assertFalse(presenter.state.value.saving)
        assertNull(presenter.state.value.error)
    }

    @Test
    fun generationFailureAllowsRetryWithoutAnotherProfile() = runTest {
        val fixture = Fixture().apply { bootstrapAction = { error("private backend detail") } }
        val presenter = fixture.presenter(backgroundScope)
        var completed = 0
        presenter.save { completed++ }
        runCurrent()
        val firstId = assertNotNull(fixture.profile).id
        assertEquals(OnboardingError.GENERATION_FAILED, presenter.state.value.error)
        assertFalse(presenter.state.value.saving)
        assertEquals(0, completed)
        fixture.bootstrapAction = { fixture.bundle }
        presenter.save { completed++ }
        runCurrent()
        assertEquals(firstId, fixture.profile?.id)
        assertEquals(1, completed)
        assertNull(presenter.state.value.error)
    }

    @Test
    fun profileWriteFailureDoesNotGenerateOrComplete() = runTest {
        val fixture = Fixture().apply { failWrite = true }
        val presenter = fixture.presenter(backgroundScope)
        presenter.save { fail("Must not complete") }
        runCurrent()
        assertEquals(OnboardingError.SAVE_FAILED, presenter.state.value.error)
        assertEquals(0, fixture.bootstrapCalls)
        assertFalse(presenter.state.value.saving)
    }

    @Test
    fun cancellationIsNotReportedAsFailure() = runTest {
        val fixture = Fixture().apply { bootstrapAction = { throw CancellationException() } }
        val presenter = fixture.presenter(backgroundScope)
        presenter.save { fail("Must not complete") }
        runCurrent()
        assertFalse(presenter.state.value.saving)
        assertNull(presenter.state.value.error)
    }

    @Test
    fun duplicateSaveAndNavigationWhileSavingAreIgnored() = runTest {
        val fixture = Fixture().apply { bootstrapAction = { awaitCancellation() } }
        val presenter = fixture.presenter(backgroundScope)
        presenter.update { it.copy(currentStep = 13) }
        presenter.save {}
        presenter.save {}
        presenter.prevStep()
        runCurrent()
        assertEquals(1, fixture.writes)
        assertEquals(1, fixture.bootstrapCalls)
        assertEquals(13, presenter.state.value.currentStep)
    }

    @Test
    fun invalidMeasurementsCannotBeReplacedWithSilentDefaults() = runTest {
        for (weight in listOf("", "broken", "NaN", "Infinity", "0", "-5")) {
            val fixture = Fixture()
            val presenter = fixture.presenter(backgroundScope)
            presenter.update { it.copy(weightKg = weight, currentStep = 1) }
            presenter.nextStep()
            assertEquals(1, presenter.state.value.currentStep)
            presenter.save { fail("Invalid input must not complete") }
            runCurrent()
            assertEquals(OnboardingError.INVALID_INPUT, presenter.state.value.error)
            assertEquals(0, fixture.writes)
        }
    }

    @Test
    fun incompleteBootstrapWithPlansButNoAdviceIsRetried() = runTest {
        val fixture = Fixture().apply {
            trainingReady = true
            nutritionReady = true
            epoch = 1L
        }
        fixture.presenter(backgroundScope).save {}
        runCurrent()
        assertEquals(1, fixture.bootstrapCalls)
        assertTrue(fixture.adviceReady)
    }

    @Test
    fun bootstrapRecoversEpochAfterPartialCommit() = runTest {
        val fixture = Fixture().apply {
            trainingReady = true
            nutritionReady = true
            adviceReady = true
        }
        fixture.presenter(backgroundScope).save {}
        runCurrent()
        assertNotNull(fixture.epoch)
    }

    @Test
    fun forcedBootstrapDoesNotSkipExistingPlans() = runTest {
        val fixture = Fixture()
        fixture.presenter(backgroundScope).save {}
        runCurrent()
        assertTrue(fixture.bootstrap(force = true))
        assertEquals(2, fixture.bootstrapCalls)
    }

    @Test
    fun changedInputRegeneratesExistingPlansWithoutChangingProfileId() = runTest {
        val fixture = Fixture()
        val presenter = fixture.presenter(backgroundScope)
        presenter.save {}
        runCurrent()
        val profileId = fixture.profile?.id
        presenter.update { it.copy(weightKg = "82") }
        presenter.save {}
        runCurrent()
        assertEquals(profileId, fixture.profile?.id)
        assertEquals(82.0, fixture.profile?.weightKg)
        assertEquals(2, fixture.bootstrapCalls)
    }

    private class Fixture {
        var profile: Profile? = null
        var writes = 0
        var failWrite = false
        var bootstrapCalls = 0
        var trainingReady = false
        var nutritionReady = false
        var adviceReady = false
        var epoch: Long? = null
        val trainingPlan = TrainingPlan(0, emptyList())
        val nutritionPlan = NutritionPlan(0, emptyMap(), emptyList())
        val bundle = CoachBundle(trainingPlan, nutritionPlan, Advice(listOf("test advice")))
        var bootstrapAction: suspend () -> CoachBundle = { bundle }
        val profiles = object : ProfileRepository {
            override suspend fun getProfile() = profile
            override suspend fun upsertProfile(profile: Profile) {
                if (failWrite) error("private DB detail")
                writes++
                this@Fixture.profile = profile
            }
            override suspend fun clearAll() { profile = null }
        }
        val ai = object : AiTrainerRepository {
            override suspend fun bootstrap(profile: Profile, weekIndex: Int): DataResult<CoachBundle> {
                bootstrapCalls++
                return DataResult.Success(bootstrapAction())
            }
            override suspend fun generateTrainingPlan(profile: Profile, weekIndex: Int) = DataResult.Success(trainingPlan)
            override suspend fun generateNutritionPlan(profile: Profile, weekIndex: Int) = DataResult.Success(nutritionPlan)
            override suspend fun getSleepAdvice(profile: Profile) = DataResult.Success(Advice(emptyList()))
        }
        val training = object : TrainingRepository {
            override suspend fun generatePlan(profile: Profile, weekIndex: Int) = trainingPlan
            override suspend fun savePlan(plan: TrainingPlan) { trainingReady = true }
            override suspend fun hasPlan(weekIndex: Int) = trainingReady
            override suspend fun deleteWeeksFrom(weekIndex: Int) { trainingReady = false }
            override suspend fun logSet(workoutId: String, set: WorkoutSet) = Unit
            override fun observeWorkouts() = MutableStateFlow(emptyList<Workout>())
            override fun observeWorkoutsByWeek(weekIndex: Int) = observeWorkouts()
            override fun observeWorkoutProgress() = MutableStateFlow(emptyMap<String, WorkoutProgress>())
            override suspend fun saveWorkoutProgress(progress: WorkoutProgress) = Unit
            override suspend fun recentWorkoutSummaries(limit: Int) = emptyList<WorkoutSummary>()
        }
        val nutrition = object : NutritionRepository {
            override suspend fun generatePlan(profile: Profile, weekIndex: Int) = nutritionPlan
            override suspend fun savePlan(plan: NutritionPlan) { nutritionReady = true }
            override fun observePlan() = MutableStateFlow<NutritionPlan?>(null)
            override suspend fun hasPlan(weekIndex: Int) = nutritionReady
            override suspend fun setActiveWeek(weekIndex: Int) = nutritionReady
            override fun registerActiveWeek(weekIndex: Int) = Unit
            override suspend fun deleteWeeksFrom(weekIndex: Int) { nutritionReady = false }
        }
        val advice = object : AdviceRepository {
            override suspend fun getAdvice(profile: Profile, context: Map<String, Any?>) = Advice(emptyList())
            override suspend fun saveAdvice(topic: String, advice: Advice) { adviceReady = true }
            override fun observeAdvice(topic: String) = MutableStateFlow(Advice(emptyList()))
            override suspend fun hasAdvice(topic: String) = adviceReady
        }
        val cache = object : CoachCacheRepository {
            override fun bundleVersion(): Int? = null
            override fun bundleTimestampMillis(): Long? = null
            override fun markBundleFresh(version: Int, timestampMillis: Long) = Unit
            override fun clearAll() = Unit
            override fun planEpochDateMs() = epoch
            override fun setPlanEpochDate(ms: Long) { epoch = ms }
            override fun clearAllAndResetEpoch() { epoch = null }
        }
        val preferences = object : PreferencesRepository {
            override fun getLanguageTag(): String? = null
            override fun setLanguageTag(tag: String?) = Unit
            override fun getAiModelMode() = AiModelMode.FREE
            override fun setAiModelMode(mode: AiModelMode) = Unit
            override fun getUnits(): String? = null
            override fun setUnits(units: String?) = Unit
            override fun getAnalyticsConsent() = false
            override fun setAnalyticsConsent(granted: Boolean) = Unit
        }
        val analytics = object : AnalyticsRepository {
            override fun logEvent(name: String, params: Map<String, String>) = Unit
            override fun setUserProperty(key: String, value: String?) = Unit
            override fun recordNonFatal(throwable: Throwable, message: String?) = Unit
        }
        val bootstrap = BootstrapCoachData(profiles, ai, training, nutrition, advice, cache)
        fun presenter(scope: CoroutineScope) = OnboardingPresenterDelegate(
            profiles, bootstrap, preferences, scope,
            analytics = analytics, analyticsConsentPreferences = preferences,
            syncAnalyticsProfile = SyncAnalyticsProfile(preferences, analytics)
        )
    }
}
