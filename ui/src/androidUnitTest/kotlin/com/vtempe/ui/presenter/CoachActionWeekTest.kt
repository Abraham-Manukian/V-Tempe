package com.vtempe.ui.presenter

import com.vtempe.shared.data.repo.SettingsAdviceRepository
import com.vtempe.shared.domain.model.Goal
import com.vtempe.shared.domain.model.NutritionPlan
import com.vtempe.shared.domain.model.Profile
import com.vtempe.shared.domain.model.Sex
import com.vtempe.shared.domain.model.TrainingPlan
import com.vtempe.shared.domain.model.Workout
import com.vtempe.shared.domain.repository.CoachAction
import com.vtempe.shared.domain.repository.CoachActionType
import com.vtempe.shared.domain.repository.CoachResponse
import com.vtempe.shared.domain.repository.ProfileRepository
import com.vtempe.shared.domain.usecase.MaterializeCoachActions
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals

/** A "rebuild my plan" action without a week rebuilds the week the user is in, not the first one. */
class CoachActionWeekTest {
    private val profile = Profile("p", 30, Sex.MALE, 180, 80.0, Goal.MAINTAIN, 2)
    private val profiles = object : ProfileRepository {
        override suspend fun getProfile() = profile
        override suspend fun upsertProfile(profile: Profile) = Unit
        override suspend fun clearAll() = Unit
    }
    private val savedTraining = mutableListOf<Int>()
    private val savedNutrition = mutableListOf<Int>()
    private val materialize = MaterializeCoachActions(
        profiles,
        object : FakeTraining() { override suspend fun savePlan(plan: TrainingPlan) { savedTraining += plan.weekIndex } },
        object : FakeNutrition() { override suspend fun savePlan(plan: NutritionPlan) { savedNutrition += plan.weekIndex } },
        SettingsAdviceRepository(memorySettings()),
    )

    private suspend fun act(type: CoachActionType, weekIndex: Int? = null) =
        materialize(profile, CoachResponse("ok", actions = listOf(CoachAction(type, weekIndex = weekIndex))), currentWeek = 5)

    @Test
    fun rebuildWithoutAWeekTargetsTheCurrentWeek() = runTest {
        act(CoachActionType.REBUILD_TRAINING_PLAN)
        act(CoachActionType.REBUILD_NUTRITION_PLAN)

        assertEquals(listOf(5), savedTraining)
        assertEquals(listOf(5), savedNutrition)
    }

    @Test
    fun anExplicitNextWeekIsKept() = runTest {
        act(CoachActionType.REBUILD_TRAINING_PLAN, weekIndex = 6)

        assertEquals(listOf(6), savedTraining)
    }

    @Test
    fun aStaleOrInventedWeekFromTheModelRebuildsTheCurrentWeek() = runTest {
        act(CoachActionType.REBUILD_TRAINING_PLAN, weekIndex = 0)
        act(CoachActionType.REBUILD_TRAINING_PLAN, weekIndex = 40)

        assertEquals(listOf(5, 5), savedTraining)
    }

    @Test
    fun showCurrentWorkoutKeepsOnlyThatWeeksWorkouts() = runTest {
        val byWeek = (0..2).associateWith { week -> listOf(Workout(id = "w_${week}_0", date = LocalDate(2026, 10, 5 + week * 7), sets = emptyList())) }
        val saved = mutableListOf<TrainingPlan>()
        val training = object : FakeTraining() {
            override fun observeWorkouts() = MutableStateFlow(byWeek.values.flatten())
            override fun observeWorkoutsByWeek(weekIndex: Int) = MutableStateFlow(byWeek[weekIndex].orEmpty())
            override suspend fun savePlan(plan: TrainingPlan) { saved += plan }
        }
        val show = MaterializeCoachActions(profiles, training, FakeNutrition(), SettingsAdviceRepository(memorySettings()))

        show(profile, CoachResponse("ok", actions = listOf(CoachAction(CoachActionType.SHOW_CURRENT_WORKOUT))), currentWeek = 2)

        assertEquals(listOf("w_2_0"), saved.single().workouts.map { it.id }, "earlier weeks are not pulled into week 2")
        assertEquals(2, saved.single().weekIndex)
    }
}
