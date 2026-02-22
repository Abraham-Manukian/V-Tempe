package com.vtempe.shared.domain.usecase

import com.vtempe.shared.data.repo.AiResponseCache
import com.vtempe.shared.domain.model.*
import com.vtempe.shared.domain.repository.*
import com.vtempe.shared.domain.util.DataResult
import com.vtempe.shared.domain.util.CoachDataFreshness
import kotlinx.coroutines.flow.Flow
import io.github.aakira.napier.Napier
import kotlinx.datetime.Clock

class GenerateTrainingPlan(
    private val profileRepository: ProfileRepository,
    private val trainingRepository: TrainingRepository,
) {
    suspend operator fun invoke(weekIndex: Int): TrainingPlan {
        val profile = profileRepository.getProfile() ?: error("Profile required")
        return trainingRepository.generatePlan(profile, weekIndex)
    }
}

class LogWorkoutSet(
    private val trainingRepository: TrainingRepository
) {
    suspend operator fun invoke(workoutId: String, set: WorkoutSet) {
        trainingRepository.logSet(workoutId, set)
    }
}

class GenerateNutritionPlan(
    private val profileRepository: ProfileRepository,
    private val nutritionRepository: NutritionRepository
) {
    suspend operator fun invoke(weekIndex: Int): NutritionPlan {
        val profile = profileRepository.getProfile() ?: error("Profile required")
        return nutritionRepository.generatePlan(profile, weekIndex)
    }
}

class BootstrapCoachData(
    private val profileRepository: ProfileRepository,
    private val aiTrainerRepository: AiTrainerRepository,
    private val trainingRepository: TrainingRepository,
    private val nutritionRepository: NutritionRepository,
    private val adviceRepository: AdviceRepository,
    private val aiResponseCache: AiResponseCache
) {
    suspend operator fun invoke(weekIndex: Int = 0): Boolean {
        val profile = profileRepository.getProfile() ?: return false
        val bundleResult = aiTrainerRepository.bootstrap(profile, weekIndex)
        val bundle = when (bundleResult) {
            is DataResult.Success -> bundleResult.data
            is DataResult.Failure -> {
                Napier.w(
                    message = "Bootstrap bundle failed: ${bundleResult.reason} ${bundleResult.message.orEmpty()}",
                    throwable = bundleResult.throwable
                )
                null
            }
        }

        val trainingPlan = bundle?.trainingPlan ?: trainingRepository.generatePlan(profile, weekIndex)
        trainingRepository.savePlan(trainingPlan)

        val nutritionPlan = bundle?.nutritionPlan ?: nutritionRepository.generatePlan(profile, weekIndex)
        nutritionRepository.savePlan(nutritionPlan)

        val advice = bundle?.sleepAdvice ?: adviceRepository.getAdvice(profile, mapOf("topic" to "sleep"))
        adviceRepository.saveAdvice("sleep", advice)

        aiResponseCache.markBundleFresh(
            version = CoachDataFreshness.SCHEMA_VERSION,
            timestampMillis = Clock.System.now().toEpochMilliseconds()
        )

        return true
    }
}



class EnsureCoachData(
    private val profileRepository: ProfileRepository,
    private val trainingRepository: TrainingRepository,
    private val nutritionRepository: NutritionRepository,
    private val adviceRepository: AdviceRepository,
    private val bootstrapCoachData: BootstrapCoachData,
    private val aiResponseCache: AiResponseCache,
) {
    suspend operator fun invoke(weekIndex: Int = 0, force: Boolean = false): Boolean {
        if (profileRepository.getProfile() == null) return false
        val now = Clock.System.now().toEpochMilliseconds()
        val version = aiResponseCache.bundleVersion() ?: -1
        val versionMismatch = version < CoachDataFreshness.SCHEMA_VERSION
        val stale = aiResponseCache.bundleTimestampMillis()
            ?.let { now - it > CoachDataFreshness.STALE_AFTER_MILLIS }
            ?: true
        val needsRefresh = force || versionMismatch || stale
        val needsTraining = needsRefresh || !trainingRepository.hasPlan(weekIndex)
        val needsNutrition = needsRefresh || !nutritionRepository.hasPlan(weekIndex)
        val needsAdvice = needsRefresh || !adviceRepository.hasAdvice("sleep")
        if (versionMismatch) {
            aiResponseCache.clearAll()
        }
        if (!needsTraining && !needsNutrition && !needsAdvice) return true
        return bootstrapCoachData(weekIndex)
    }
}

class SyncWithBackend(
    private val syncRepository: SyncRepository
) {
    suspend operator fun invoke(): Boolean = syncRepository.syncAll()
}

class ValidateSubscription(
    private val purchasesRepository: PurchasesRepository
) {
    suspend operator fun invoke(): Boolean = purchasesRepository.isSubscriptionActive()
}

interface AnalyticsLogger {
    fun log(event: String, params: Map<String, Any?> = emptyMap())
}

object AnalyticsEvents {
    const val OnboardingCompleted = "onboarding_completed"
    const val WorkoutStarted = "workout_started"
    const val WorkoutCompleted = "workout_completed"
    const val SetLogged = "set_logged"
    const val MealCompleted = "meal_completed"
    const val AdviceOpened = "advice_opened"
    const val PaywallViewed = "paywall_viewed"
    const val PurchaseSuccess = "purchase_success"
    const val SubscriptionRenewed = "subscription_renewed"
    const val ChurnWarning = "churn_warning"
}


