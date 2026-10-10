package com.vtempe.ui.presenter

import com.vtempe.shared.data.repo.SettingsAdviceRepository
import com.vtempe.shared.data.repo.SleepStore
import com.vtempe.shared.data.repo.WeightStore
import com.vtempe.shared.domain.model.Advice
import com.vtempe.shared.domain.model.Goal
import com.vtempe.shared.domain.model.Macros
import com.vtempe.shared.domain.model.Meal
import com.vtempe.shared.domain.model.NutritionPlan
import com.vtempe.shared.domain.model.Profile
import com.vtempe.shared.domain.model.Sex
import com.vtempe.shared.domain.model.Workout
import com.vtempe.shared.domain.model.WorkoutSet
import com.vtempe.shared.domain.repository.AiTrainerRepository
import com.vtempe.shared.domain.repository.NutritionRepository
import com.vtempe.shared.domain.repository.ProfileRepository
import com.vtempe.shared.domain.repository.TrainingRepository
import com.vtempe.shared.domain.usecase.BootstrapCoachData
import com.vtempe.shared.domain.usecase.EnsureCoachData
import com.vtempe.shared.domain.usecase.RefreshSleepAdvice
import com.vtempe.ui.util.toShortKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Clock
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import kotlinx.datetime.todayIn
import java.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** VT-AUD-069 (Home shows the user's own plan, not a fixed sample) and VT-AUD-071 (Retry re-reads). */
class HomeStateTest {
    private val today = Clock.System.todayIn(TimeZone.currentSystemDefault())
    private val profile = Profile("p", 30, Sex.MALE, 180, 80.0, Goal.MAINTAIN, 2)

    private fun TestScope.home(training: TrainingRepository, nutrition: NutritionRepository): HomePresenterDelegate {
        val profiles = object : ProfileRepository {
            override suspend fun getProfile() = profile
            override suspend fun upsertProfile(profile: Profile) = Unit
            override suspend fun clearAll() = Unit
        }
        val advice = SettingsAdviceRepository(memorySettings())
        val ai = stub<AiTrainerRepository> { error("no AI call expected") }
        val ensure = EnsureCoachData(
            profiles, training, nutrition, advice,
            BootstrapCoachData(profiles, ai, training, nutrition, advice, FakeCoachCache()),
            FakeCoachCache(), backgroundScope, RefreshSleepAdvice(profiles, ai, advice),
        )
        kotlinx.coroutines.runBlocking { advice.saveAdvice("sleep", Advice(listOf("tip"))) }
        return HomePresenterDelegate(training, nutrition, ensure, SleepStore(memorySettings()), WeightStore(memorySettings()), backgroundScope)
    }

    private fun meal(p: Int, f: Int, c: Int) = Meal("m", listOf("x"), p * 4 + f * 9 + c * 4, Macros(p, f, c, p * 4 + f * 9 + c * 4))

    @Test
    fun todaysCardsDescribeTheStoredPlan() = runTest {
        val workouts = listOf(
            Workout("w1", "Legs", today, listOf(WorkoutSet("squat", 8, sets = 4), WorkoutSet("lunge", 10, sets = 3))),
            Workout("w2", "Upper", today.plus(DatePeriod(days = 1)), listOf(WorkoutSet("bench_press", 8))),
        )
        val menu = NutritionPlan(0, mapOf(today.dayOfWeek.toShortKey() to listOf(meal(30, 10, 50), meal(40, 15, 60))), emptyList())
        val p = home(FakeTraining(workouts), FakeNutrition(menu))
        runCurrent()

        assertEquals(TodayWorkout("Legs", exercises = 2, sets = 7), p.state.value.todayWorkout)
        assertEquals(7, p.state.value.todaySets, "sets, not exercises")
        assertEquals(Macros(70, 25, 110, menu.mealsByDay.values.single().sumOf { it.kcal }), p.state.value.todayMacros)
    }

    @Test
    fun restDayAndMissingPlansAreSaidAsSuch() = runTest {
        val tomorrowOnly = listOf(Workout("w2", "Upper", today.plus(DatePeriod(days = 1)), listOf(WorkoutSet("bench_press", 8))))
        val rest = home(FakeTraining(tomorrowOnly), FakeNutrition(null))
        runCurrent()
        assertNull(rest.state.value.todayWorkout)
        assertTrue(rest.state.value.hasTrainingPlan, "a rest day, not a missing plan")
        assertNull(rest.state.value.todayMacros)

        val empty = home(FakeTraining(), FakeNutrition(null))
        runCurrent()
        assertFalse(empty.state.value.hasTrainingPlan)
    }

    @Test
    fun retryReadsAgainAfterAFailedSubscription() = runTest {
        val workouts = listOf(Workout("w1", "Legs", today, listOf(WorkoutSet("squat", 8))))
        var subscriptions = 0
        val flaky = object : FakeTraining(workouts) {
            override fun observeWorkouts(): Flow<List<Workout>> {
                subscriptions++
                return if (subscriptions == 1) flow { throw IOException("db busy") } else flowOf(workouts)
            }
        }
        val p = home(flaky, FakeNutrition(null))
        runCurrent()
        assertNotNull(p.state.value.errorMessage)

        p.refresh()
        runCurrent()
        assertEquals(2, subscriptions)
        assertNull(p.state.value.errorMessage)
        assertNotNull(p.state.value.todayWorkout)
    }
}
