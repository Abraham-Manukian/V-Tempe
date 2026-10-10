package com.vtempe.ui.presenter

import com.vtempe.shared.data.repo.SettingsAdviceRepository
import com.vtempe.shared.domain.model.Advice
import com.vtempe.shared.domain.model.Goal
import com.vtempe.shared.domain.model.Profile
import com.vtempe.shared.domain.model.Sex
import com.vtempe.shared.domain.repository.AiTrainerRepository
import com.vtempe.shared.domain.repository.ProfileRepository
import com.vtempe.shared.domain.usecase.BootstrapCoachData
import com.vtempe.shared.domain.usecase.EnsureCoachData
import com.vtempe.shared.domain.usecase.LogWorkoutSet
import com.vtempe.shared.domain.usecase.RefreshSleepAdvice
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** VT-AUD-065: the open workout and its timers live in the presenter, as timestamps. */
class WorkoutSessionTest {
    private var now = 1_000_000L

    private fun TestScope.presenter(): WorkoutPresenterDelegate {
        val profiles = object : ProfileRepository {
            override suspend fun getProfile() = Profile("p", 30, Sex.MALE, 180, 80.0, Goal.MAINTAIN, 2)
            override suspend fun upsertProfile(profile: Profile) = Unit
            override suspend fun clearAll() = Unit
        }
        val training = FakeTraining()
        val nutrition = FakeNutrition()
        val advice = SettingsAdviceRepository(memorySettings()).also { runBlocking { it.saveAdvice("sleep", Advice(listOf("tip"))) } }
        val ai = stub<AiTrainerRepository> { error("no AI call expected") }
        val ensure = EnsureCoachData(
            profiles, training, nutrition, advice,
            BootstrapCoachData(profiles, ai, training, nutrition, advice, FakeCoachCache()),
            FakeCoachCache(), backgroundScope, RefreshSleepAdvice(profiles, ai, advice),
        )
        return WorkoutPresenterDelegate(training, LogWorkoutSet(training), ensure, profiles, FakeCoachCache(), backgroundScope) { now }
    }

    @Test
    fun theOpenWorkoutAndItsClockSurviveLeavingAndComingBack() = runTest {
        val p = presenter()
        runCurrent()
        p.openWorkout("w1")
        now += 125_000
        p.startRest(90)
        now += 30_000

        // The screen is recreated or the user visits the chat and returns: it reads the same session.
        val session = p.state.value.session!!
        assertEquals("w1", session.workoutId)
        assertEquals(155, session.elapsedSeconds(now))
        assertEquals(60, session.restRemainingSeconds(now))

        p.openWorkout("w1") // reopening the same workout keeps its clock
        assertEquals(155, p.state.value.session!!.elapsedSeconds(now))

        p.skipRest()
        assertEquals(0, p.state.value.session!!.restRemainingSeconds(now))

        now += 120_000
        p.startRest(60)
        now += 90_000
        assertEquals(0, p.state.value.session!!.restRemainingSeconds(now), "rest that ran out while away is over")

        p.closeWorkout()
        assertNull(p.state.value.session)
        p.openWorkout("w2")
        assertEquals(0, p.state.value.session!!.elapsedSeconds(now), "another workout starts its own clock")
    }
}
