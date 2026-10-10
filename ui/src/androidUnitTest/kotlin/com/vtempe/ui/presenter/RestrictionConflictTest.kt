package com.vtempe.ui.presenter

import com.vtempe.shared.data.repo.SettingsAdviceRepository
import com.vtempe.shared.data.repo.SleepStore
import com.vtempe.shared.data.repo.WeightStore
import com.vtempe.shared.domain.model.Advice
import com.vtempe.shared.domain.model.Goal
import com.vtempe.shared.domain.model.NutritionPlan
import com.vtempe.shared.domain.model.Profile
import com.vtempe.shared.domain.model.Sex
import com.vtempe.shared.domain.model.TrainingPlan
import com.vtempe.shared.domain.repository.AiTrainerRepository
import com.vtempe.shared.domain.repository.CoachBundle
import com.vtempe.shared.domain.repository.ProfileRepository
import com.vtempe.shared.domain.training.NoSafeTrainingPlanException
import com.vtempe.shared.domain.usecase.BootstrapCoachData
import com.vtempe.shared.domain.usecase.EnsureCoachData
import com.vtempe.shared.domain.usecase.RefreshSleepAdvice
import com.vtempe.shared.domain.util.DataResult
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import java.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals

/** A plan refused because restrictions leave nothing safe is named as such, not as a network error. */
class RestrictionConflictTest {
    private val profile = Profile("p", 30, Sex.MALE, 180, 80.0, Goal.MAINTAIN, 2)

    private fun TestScope.ensure(failure: Exception): EnsureCoachData {
        val profiles = object : ProfileRepository {
            override suspend fun getProfile() = profile
            override suspend fun upsertProfile(profile: Profile) = Unit
            override suspend fun clearAll() = Unit
        }
        val training = object : FakeTraining() {
            override suspend fun hasPlan(weekIndex: Int) = false
            override suspend fun generatePlan(profile: Profile, weekIndex: Int): TrainingPlan = throw failure
        }
        val nutrition = FakeNutrition()
        val offline = object : AiTrainerRepository {
            override suspend fun generateTrainingPlan(profile: Profile, weekIndex: Int): DataResult<TrainingPlan> = DataResult.Failure(DataResult.Reason.Network)
            override suspend fun generateNutritionPlan(profile: Profile, weekIndex: Int): DataResult<NutritionPlan> = DataResult.Failure(DataResult.Reason.Network)
            override suspend fun getSleepAdvice(profile: Profile): DataResult<Advice> = DataResult.Failure(DataResult.Reason.Network)
            override suspend fun bootstrap(profile: Profile, weekIndex: Int): DataResult<CoachBundle> = DataResult.Failure(DataResult.Reason.Network)
        }
        val advice = SettingsAdviceRepository(memorySettings())
        return EnsureCoachData(
            profiles, training, nutrition, advice,
            BootstrapCoachData(profiles, offline, training, nutrition, advice, FakeCoachCache()),
            FakeCoachCache(), backgroundScope, RefreshSleepAdvice(profiles, offline, advice),
        )
    }

    private fun TestScope.home(failure: Exception) = HomePresenterDelegate(
        FakeTraining(), FakeNutrition(), ensure(failure), SleepStore(memorySettings()), WeightStore(memorySettings()), backgroundScope,
    )

    @Test
    fun homeNamesARestrictionConflict() = runTest {
        val home = home(NoSafeTrainingPlanException("every exercise is ruled out"))
        runCurrent()
        assertEquals(RESTRICTIONS_CONFLICT, home.state.value.errorMessage)
    }

    @Test
    fun anOrdinaryFailureStaysAGenericLoadError() = runTest {
        val home = home(IOException("offline"))
        runCurrent()
        assertEquals("load_failed", home.state.value.errorMessage)
    }
}
