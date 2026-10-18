package com.vtempe.ui.presenter

import com.russhwolf.settings.Settings
import com.vtempe.shared.data.repo.SettingsAdviceRepository
import com.vtempe.shared.data.repo.SleepStore
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
import com.vtempe.shared.domain.repository.CoachBundle
import com.vtempe.shared.domain.repository.CoachCacheRepository
import com.vtempe.shared.domain.repository.NutritionRepository
import com.vtempe.shared.domain.repository.ProfileRepository
import com.vtempe.shared.domain.repository.TrainingRepository
import com.vtempe.shared.domain.usecase.BootstrapCoachData
import com.vtempe.shared.domain.usecase.EnsureCoachData
import com.vtempe.shared.domain.usecase.RefreshSleepAdvice
import com.vtempe.shared.domain.util.DataResult
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Clock
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn
import java.lang.reflect.Proxy
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull

/** VT-AUD-041–047: what the Sleep and Progress screens show, against real stores. */
class SleepScreenStateTest {

    private val day = LocalDate(2026, 10, 7) // a Wednesday
    private val profile = Profile("p", 30, Sex.MALE, 180, 80.0, Goal.MAINTAIN, 2)

    private class Ai : AiTrainerRepository {
        var calls = 0
        val answers = ArrayDeque<CompletableDeferred<DataResult<Advice>>>()
        override suspend fun getSleepAdvice(profile: Profile): DataResult<Advice> {
            calls++
            return answers.removeFirst().await()
        }
        override suspend fun generateTrainingPlan(profile: Profile, weekIndex: Int): DataResult<TrainingPlan> = error("unused")
        override suspend fun generateNutritionPlan(profile: Profile, weekIndex: Int): DataResult<NutritionPlan> = error("unused")
        override suspend fun bootstrap(profile: Profile, weekIndex: Int): DataResult<CoachBundle> = error("unused")
    }

    private inner class Fixture(val settings: Settings = memorySettings()) {
        val ai = Ai()
        val advice = SettingsAdviceRepository(settings)
        val sleep = SleepStore(settings)
        val dates = MutableStateFlow(day)
        private val profiles = object : ProfileRepository {
            override suspend fun getProfile() = profile
            override suspend fun upsertProfile(profile: Profile) = Unit
            override suspend fun clearAll() = Unit
        }

        fun presenter(scope: TestScope) =
            SleepPresenterDelegate(RefreshSleepAdvice(profiles, ai, advice), advice, sleep, scope.backgroundScope, dates)

        fun answer(result: DataResult<Advice>) = CompletableDeferred(result).also { ai.answers.addLast(it) }
    }

    @Test
    fun storedAdviceFromAnySourceIsShownAndSurvivesRecreation() = runTest {
        val f = Fixture()
        val first = f.presenter(this)
        runCurrent()
        assertEquals(emptyList(), first.state.value.tips)

        f.advice.saveAdvice("sleep", Advice(listOf("Keep a fixed wake time."))) // e.g. bootstrap or chat
        runCurrent()
        assertEquals(listOf("Keep a fixed wake time."), first.state.value.tips)

        val recreated = Fixture(f.settings).presenter(this) // process restart: same storage
        runCurrent()
        assertEquals(listOf("Keep a fixed wake time."), recreated.state.value.tips)
    }

    @Test
    fun repeatedTapsShareOneRequestAndLoadingLastsUntilItEnds() = runTest {
        val f = Fixture()
        val presenter = f.presenter(this)
        val pending = CompletableDeferred<DataResult<Advice>>().also { f.ai.answers.addLast(it) }
        presenter.refreshAdvice()
        presenter.refreshAdvice()
        runCurrent()
        assertEquals(1, f.ai.calls)
        assertEquals(SleepAdviceStatus.LOADING, presenter.state.value.adviceStatus)

        pending.complete(DataResult.Success(Advice(listOf("Dim the lights an hour before bed."))))
        runCurrent()
        assertEquals(SleepAdviceStatus.IDLE, presenter.state.value.adviceStatus)
        assertEquals(listOf("Dim the lights an hour before bed."), presenter.state.value.tips)
    }

    @Test
    fun failuresAreVisibleKeepTheOldTipsAndCanBeRetried() = runTest {
        val f = Fixture()
        f.advice.saveAdvice("sleep", Advice(listOf("Old tip")))
        val presenter = f.presenter(this)

        f.answer(DataResult.Failure(DataResult.Reason.Network))
        presenter.refreshAdvice(); runCurrent()
        assertEquals(SleepAdviceStatus.OFFLINE, presenter.state.value.adviceStatus)
        assertEquals(listOf("Old tip"), presenter.state.value.tips)

        f.answer(DataResult.Success(Advice(listOf("Cached tip")), fromCache = true))
        presenter.refreshAdvice(); runCurrent()
        assertEquals(SleepAdviceStatus.OFFLINE, presenter.state.value.adviceStatus, "a cached answer is not a fresh one")
        assertEquals(listOf("Old tip"), presenter.state.value.tips)

        f.answer(DataResult.Failure(DataResult.Reason.Http, code = 500))
        presenter.refreshAdvice(); runCurrent()
        assertEquals(SleepAdviceStatus.FAILED, presenter.state.value.adviceStatus)

        f.answer(DataResult.Success(Advice(listOf("New tip"))))
        presenter.refreshAdvice(); runCurrent()
        assertEquals(SleepAdviceStatus.IDLE, presenter.state.value.adviceStatus)
        assertEquals(listOf("New tip"), presenter.state.value.tips)
    }

    @Test
    fun diaryFollowsRestoresAndTheCalendarDay() = runTest {
        val f = Fixture()
        f.sleep.logSleep(day.toString(), 480, "Before")
        val presenter = f.presenter(this)
        runCurrent()
        assertEquals(480, presenter.state.value.loggedMinutes)

        f.sleep.restoreRaw("""{"${day}":240,"2026-10-08":30}""") // sync pulled another device's diary
        runCurrent()
        assertEquals(240, presenter.state.value.loggedMinutes)

        f.dates.value = LocalDate(2026, 10, 8) // midnight passed
        runCurrent()
        assertEquals(LocalDate(2026, 10, 8), presenter.state.value.date)
        assertEquals(30, presenter.state.value.loggedMinutes)
        assertEquals(listOf(null, null, 240, 30, null, null, null), presenter.state.value.weeklyMinutes, "30 min is not 0 h")

        f.dates.value = LocalDate(2026, 10, 12) // next week, nothing logged yet
        runCurrent()
        assertNull(presenter.state.value.loggedMinutes)
        assertEquals(List(7) { null }, presenter.state.value.weeklyMinutes)
    }

    @Test
    fun noteOnlyEditKeepsTheExactStoredDuration() = runTest {
        val f = Fixture()
        f.sleep.restoreRaw("""{"${day}":615}""")
        val presenter = f.presenter(this)
        runCurrent()
        presenter.logSleep(day, minutes = null, notes = "Woke up once")
        runCurrent()
        assertEquals(615, f.sleep.entryFor(day))
        assertEquals("Woke up once", presenter.state.value.loggedNotes)

        presenter.logSleep(day, minutes = 840, notes = "Woke up once")
        assertEquals(840, f.sleep.entryFor(day))
    }

    @Test
    fun progressShowsTheSameSleepWeekAsTheSleepScreen() = runTest {
        val f = Fixture()
        val today = Clock.System.todayIn(TimeZone.currentSystemDefault())
        val progress = ProgressPresenterDelegate(NoTraining, NoNutrition, NoCache, f.sleep, backgroundScope)
        runCurrent()
        assertEquals(List(7) { null }, progress.state.value.sleepMinutesWeek)

        f.sleep.logSleep(today.toString(), 450)
        runCurrent()
        val week = progress.state.value.sleepMinutesWeek
        assertEquals(450, week[today.dayOfWeek.ordinal])
        assertEquals(450, week.averageLoggedMinutes(), "days without an entry are not counted as zero")
        assertEquals("7:30", formatSleepMinutes(week.averageLoggedMinutes()))
    }

    @Test
    fun missingAdviceWithPlansInPlaceFetchesOnlyTheAdvice() = runTest {
        val f = Fixture()
        var bootstraps = 0
        val ai = object : AiTrainerRepository by f.ai {
            override suspend fun bootstrap(profile: Profile, weekIndex: Int): DataResult<CoachBundle> {
                bootstraps++
                return DataResult.Failure(DataResult.Reason.Network)
            }
        }
        val profiles = object : ProfileRepository {
            override suspend fun getProfile() = profile
            override suspend fun upsertProfile(profile: Profile) = Unit
            override suspend fun clearAll() = Unit
        }
        val training = object : TrainingRepository by NoTraining { override suspend fun hasPlan(weekIndex: Int) = true }
        val nutrition = object : NutritionRepository by NoNutrition { override suspend fun hasPlan(weekIndex: Int) = true }
        val refresh = RefreshSleepAdvice(profiles, ai, f.advice)
        val ensure = EnsureCoachData(
            profiles, training, nutrition, f.advice,
            BootstrapCoachData(profiles, ai, training, nutrition, f.advice, NoCache), NoCache, backgroundScope, refresh,
        )
        f.answer(DataResult.Success(Advice(listOf("Fresh tip"))))

        ensure()
        runCurrent()

        assertEquals(0, bootstraps, "this week's plans must not be regenerated for missing advice")
        assertEquals(1, f.ai.calls)
        assertEquals(listOf("Fresh tip"), f.advice.getAdvice(profile, mapOf("topic" to "sleep")).messages)
    }

    @Test
    fun aFailedFirstBootstrapLeavesTheAdviceToBeFetchedOnceTheNetworkIsBack() = runTest {
        val f = Fixture()
        val ai = object : AiTrainerRepository by f.ai {
            override suspend fun bootstrap(profile: Profile, weekIndex: Int): DataResult<CoachBundle> =
                DataResult.Failure(DataResult.Reason.Network)
        }
        val profiles = object : ProfileRepository {
            override suspend fun getProfile() = profile
            override suspend fun upsertProfile(profile: Profile) = Unit
            override suspend fun clearAll() = Unit
        }
        var trainingSaved = false
        var nutritionSaved = false
        val training = object : TrainingRepository by NoTraining {
            override suspend fun generatePlan(profile: Profile, weekIndex: Int) = TrainingPlan(weekIndex, emptyList())
            override suspend fun savePlan(plan: TrainingPlan) { trainingSaved = true }
            override suspend fun hasPlan(weekIndex: Int) = trainingSaved
        }
        val nutrition = object : NutritionRepository by NoNutrition {
            override suspend fun generatePlan(profile: Profile, weekIndex: Int) = NutritionPlan(weekIndex, emptyMap(), emptyList())
            override suspend fun savePlan(plan: NutritionPlan) { nutritionSaved = true }
            override suspend fun hasPlan(weekIndex: Int) = nutritionSaved
        }
        val ensure = EnsureCoachData(
            profiles, training, nutrition, f.advice,
            BootstrapCoachData(profiles, ai, training, nutrition, f.advice, NoCache), NoCache, backgroundScope,
            RefreshSleepAdvice(profiles, ai, f.advice),
        )

        ensure() // offline: offline plans, no advice
        assertFalse(f.advice.hasAdvice("sleep"), "no empty record that would count as advice")

        f.answer(DataResult.Success(Advice(listOf("Fresh tip"))))
        ensure() // back online, plans in place
        runCurrent()

        assertEquals(listOf("Fresh tip"), f.advice.getAdvice(profile, mapOf("topic" to "sleep")).messages)
    }

    @Test
    fun adviceThatKeepsFailingIsNotRequestedOnEveryScreenOpen() = runTest {
        val f = Fixture()
        val profiles = object : ProfileRepository {
            override suspend fun getProfile() = profile
            override suspend fun upsertProfile(profile: Profile) = Unit
            override suspend fun clearAll() = Unit
        }
        val training = object : TrainingRepository by NoTraining { override suspend fun hasPlan(weekIndex: Int) = true }
        val nutrition = object : NutritionRepository by NoNutrition { override suspend fun hasPlan(weekIndex: Int) = true }
        var now = 0L
        val ensure = EnsureCoachData(
            profiles, training, nutrition, f.advice,
            BootstrapCoachData(profiles, f.ai, training, nutrition, f.advice, NoCache), NoCache, backgroundScope,
            RefreshSleepAdvice(profiles, f.ai, f.advice), nowMillis = { now },
        )
        repeat(4) { f.answer(DataResult.Failure(DataResult.Reason.Network)) }

        repeat(3) { ensure(); runCurrent() } // Home, Workout, Nutrition open while offline
        assertEquals(1, f.ai.calls, "one attempt, not one per screen")

        now += 5 * 60 * 1000L
        ensure(); runCurrent()
        assertEquals(2, f.ai.calls, "tried again after the gap")
    }

    @Test
    fun aFailedBootstrapIsNotRepeatedByTheNextScreenThatWaitedForIt() = runTest {
        val f = Fixture()
        var bootstraps = 0
        val ai = object : AiTrainerRepository by f.ai {
            override suspend fun bootstrap(profile: Profile, weekIndex: Int): DataResult<CoachBundle> {
                bootstraps++
                return DataResult.Failure(DataResult.Reason.Network)
            }
        }
        val profiles = object : ProfileRepository {
            override suspend fun getProfile() = profile
            override suspend fun upsertProfile(profile: Profile) = Unit
            override suspend fun clearAll() = Unit
        }
        var trainingSaved = false
        var nutritionSaved = false
        val training = object : TrainingRepository by NoTraining {
            override suspend fun generatePlan(profile: Profile, weekIndex: Int) = TrainingPlan(weekIndex, emptyList())
            override suspend fun savePlan(plan: TrainingPlan) { trainingSaved = true }
            override suspend fun hasPlan(weekIndex: Int) = trainingSaved
        }
        val nutrition = object : NutritionRepository by NoNutrition {
            override suspend fun generatePlan(profile: Profile, weekIndex: Int) = NutritionPlan(weekIndex, emptyMap(), emptyList())
            override suspend fun savePlan(plan: NutritionPlan) { nutritionSaved = true }
            override suspend fun hasPlan(weekIndex: Int) = nutritionSaved
        }
        val bootstrap = BootstrapCoachData(profiles, ai, training, nutrition, f.advice, FakeCoachCache())

        listOf(async { bootstrap(0) }, async { bootstrap(0) }).awaitAll()

        assertEquals(1, bootstraps, "the second caller sees the week done, advice or not")
    }

    @Test
    fun anEmptyAdviceRecordFromAnEarlierVersionIsFetchedAgain() = runTest {
        val f = Fixture()
        f.advice.saveAdvice("sleep", Advice(emptyList())) // what earlier versions stored after a failed bootstrap
        val profiles = object : ProfileRepository {
            override suspend fun getProfile() = profile
            override suspend fun upsertProfile(profile: Profile) = Unit
            override suspend fun clearAll() = Unit
        }
        val training = object : TrainingRepository by NoTraining { override suspend fun hasPlan(weekIndex: Int) = true }
        val nutrition = object : NutritionRepository by NoNutrition { override suspend fun hasPlan(weekIndex: Int) = true }
        val ensure = EnsureCoachData(
            profiles, training, nutrition, f.advice,
            BootstrapCoachData(profiles, f.ai, training, nutrition, f.advice, NoCache), NoCache, backgroundScope,
            RefreshSleepAdvice(profiles, f.ai, f.advice),
        )
        f.answer(DataResult.Success(Advice(listOf("Fresh tip"))))

        ensure()
        runCurrent()

        assertEquals(listOf("Fresh tip"), f.advice.getAdvice(profile, mapOf("topic" to "sleep")).messages)
    }

    private object NoTraining : TrainingRepository {
        override suspend fun generatePlan(profile: Profile, weekIndex: Int) = error("unused")
        override suspend fun logSet(workoutId: String, set: WorkoutSet) = Unit
        override fun observeWorkouts() = MutableStateFlow(emptyList<Workout>())
        override fun observeWorkoutsByWeek(weekIndex: Int) = observeWorkouts()
        override fun observeWorkoutProgress() = MutableStateFlow(emptyMap<String, WorkoutProgress>())
        override suspend fun saveWorkoutProgress(progress: WorkoutProgress) = Unit
        override suspend fun updateWorkoutProgress(workoutId: String, transform: (WorkoutProgress) -> WorkoutProgress) = Unit
        override suspend fun recentWorkoutSummaries(limit: Int) = emptyList<WorkoutSummary>()
        override suspend fun savePlan(plan: TrainingPlan) = Unit
        override suspend fun hasPlan(weekIndex: Int) = false
        override suspend fun deleteWeeksFrom(weekIndex: Int) = Unit
    }

    private object NoNutrition : NutritionRepository {
        override suspend fun generatePlan(profile: Profile, weekIndex: Int) = error("unused")
        override suspend fun savePlan(plan: NutritionPlan) = Unit
        override fun observePlan() = MutableStateFlow<NutritionPlan?>(null)
        override suspend fun hasPlan(weekIndex: Int) = false
        override suspend fun setActiveWeek(weekIndex: Int) = false
        override fun registerActiveWeek(weekIndex: Int) = Unit
        override suspend fun deleteWeeksFrom(weekIndex: Int) = Unit
    }

    private object NoCache : CoachCacheRepository {
        override fun bundleVersion(): Int? = null
        override fun bundleTimestampMillis(): Long? = null
        override fun markBundleFresh(version: Int, timestampMillis: Long) = Unit
        override fun clearAll() = Unit
        override fun planEpochDateMs(): Long? = null
        override fun setPlanEpochDate(ms: Long) = Unit
        override fun clearAllAndResetEpoch() = Unit
    }

    private fun memorySettings(): Settings {
        val values = mutableMapOf<String, String>()
        return Proxy.newProxyInstance(Settings::class.java.classLoader, arrayOf(Settings::class.java)) { _, method, args ->
            when (method.name) {
                "getStringOrNull" -> values[args!![0] as String]
                "putString" -> { values[args!![0] as String] = args[1] as String; null }
                "remove" -> { values.remove(args!![0] as String); null }
                else -> error("Unexpected Settings call: ${method.name}")
            }
        } as Settings
    }
}
