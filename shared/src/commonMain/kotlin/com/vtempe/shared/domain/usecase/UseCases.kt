package com.vtempe.shared.domain.usecase

import com.vtempe.shared.domain.model.*
import com.vtempe.shared.domain.repository.*
import com.vtempe.shared.domain.util.CoachDataFreshness
import com.vtempe.shared.domain.util.DataResult
import com.vtempe.shared.domain.util.CoachSchedule
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import io.github.aakira.napier.Napier
import kotlinx.datetime.Clock
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.minus
import kotlinx.datetime.todayIn

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
    private val coachCache: CoachCacheRepository
) {
    // Prevents duplicate network requests when multiple presenters initialise simultaneously.
    // BootstrapCoachData is a Koin `single` so this mutex is truly app-wide.
    private val mutex = Mutex()

    suspend operator fun invoke(weekIndex: Int = 0): Boolean = mutex.withLock {
        val profile = profileRepository.getProfile() ?: return@withLock false

        // Double-check after acquiring the lock: a concurrent caller may have
        // already completed the bootstrap for this week while we were waiting.
        if (trainingRepository.hasPlan(weekIndex) && nutritionRepository.hasPlan(weekIndex)) {
            Napier.d("Bootstrap week $weekIndex: already done by concurrent call — skipping")
            return@withLock true
        }
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

        val now = Clock.System.now().toEpochMilliseconds()

        // Set epoch only once — it defines week 0 forever.
        // Snap to Monday of the current week so week boundaries always align with calendar weeks.
        if (coachCache.planEpochDateMs() == null) {
            val tz = TimeZone.currentSystemDefault()
            val today = Clock.System.todayIn(tz)
            val monday = today.minus(DatePeriod(days = today.dayOfWeek.ordinal))
            coachCache.setPlanEpochDate(monday.atStartOfDayIn(tz).toEpochMilliseconds())
        }

        coachCache.markBundleFresh(
            version = CoachDataFreshness.SCHEMA_VERSION,
            timestampMillis = now
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
    private val coachCache: CoachCacheRepository,
    /** Application-level scope — outlives any individual screen, safe for background prefetch. */
    private val appScope: CoroutineScope,
) {
    /**
     * Rolling 7-day plan strategy:
     *
     *  • Computes the current weekIndex from the epoch stored on first bootstrap.
     *  • If current week's data is in DB → return immediately, no network call.
     *  • If data is missing → bootstrap from AI.
     *  • force = true (profile change): clear pre-fetched future weeks first so the
     *    new profile's plans replace any stale ones, then re-bootstrap current week.
     *  • 2 days before week end → silently pre-fetch next week using appScope
     *    (independent of any screen lifecycle).
     */
    suspend operator fun invoke(force: Boolean = false): Boolean {
        if (profileRepository.getProfile() == null) return false

        val epochMs     = coachCache.planEpochDateMs()
        val currentWeek = CoachSchedule.currentWeekIndex(epochMs)

        if (force) {
            // Invalidate any pre-fetched future weeks — they were generated for the OLD profile.
            val nextWeek = currentWeek + 1
            trainingRepository.deleteWeeksFrom(nextWeek)
            nutritionRepository.deleteWeeksFrom(nextWeek)
        }

        val needsTraining  = force || !trainingRepository.hasPlan(currentWeek)
        val needsNutrition = force || !nutritionRepository.hasPlan(currentWeek)
        val needsAdvice    = force || !adviceRepository.hasAdvice("sleep")

        if (needsTraining || needsNutrition || needsAdvice) {
            if (!bootstrapCoachData(currentWeek)) return false
        }

        // Pre-fetch next week in the background when the current week is almost over.
        // Uses appScope so the job is NOT cancelled when the triggering screen closes.
        val daysLeft = CoachSchedule.daysUntilWeekEnd(coachCache.planEpochDateMs())
        if (daysLeft <= CoachSchedule.PREFETCH_DAYS_BEFORE_EXPIRY) {
            val nextWeek = currentWeek + 1
            if (!trainingRepository.hasPlan(nextWeek) || !nutritionRepository.hasPlan(nextWeek)) {
                Napier.i("Prefetching week $nextWeek in background ($daysLeft days left in current week)")
                appScope.launch {
                    runCatching { bootstrapCoachData(nextWeek) }
                        .onFailure { Napier.w("Background prefetch week $nextWeek failed", it) }
                }
            }
        }

        return true
    }
}

/**
 * Full account reset / re-registration.
 *
 * Clears everything so the next launch starts from a clean slate:
 *  • Profile + all DB data (workouts, nutrition plans)
 *  • AI response cache
 *  • planEpochDateMs — week counter restarts from day 0 for the new user
 *
 * NOT called on regular profile edits — those keep the epoch and regenerate
 * plans for the current week via EnsureCoachData(force = true).
 */
class ResetCoachData(
    private val profileRepository: ProfileRepository,
    private val coachCache: CoachCacheRepository,
) {
    suspend operator fun invoke() {
        profileRepository.clearAll()      // DB: profile, workouts, nutrition rows
        coachCache.clearAllAndResetEpoch() // SharedPrefs: AI cache + week epoch
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

/**
 * Sends demographic analytics to Firebase — ONLY when the user has explicitly opted in
 * (see AnalyticsConsentPreferences). Everything is bucketed rather than exact, even with
 * consent: Google's own Firebase Analytics terms prohibit sending data that could reveal
 * health status (height/weight), regardless of user consent — consent covers GDPR/privacy
 * law, it does not override the platform's own data-category restrictions.
 *
 * For precise, non-bucketed analysis (e.g. "exact weight vs chosen trainer"), query the
 * server's own profile database directly instead of piping raw values through Firebase.
 */
class SyncAnalyticsProfile(
    private val preferencesRepository: PreferencesRepository,
    private val analytics: AnalyticsRepository
) {
    operator fun invoke(profile: Profile) {
        if (!preferencesRepository.getAnalyticsConsent()) return

        analytics.setUserProperty("coach_trainer_id", profile.coachTrainerId)
        analytics.setUserProperty("gender", profile.sex.name)
        analytics.setUserProperty("age_bucket", ageBucket(profile.age))
        analytics.setUserProperty("height_bucket", heightBucket(profile.heightCm))
        analytics.setUserProperty("weight_bucket", weightBucket(profile.weightKg))
        analytics.setUserProperty("budget_level", profile.budgetLevel.toString())
        analytics.setUserProperty("training_focus", profile.trainingFocus.name)
        analytics.setUserProperty("goal", profile.goal.name)
    }

    private fun ageBucket(age: Int): String = when {
        age < 18 -> "under_18"
        age < 25 -> "18_24"
        age < 35 -> "25_34"
        age < 45 -> "35_44"
        age < 55 -> "45_54"
        else -> "55_plus"
    }

    private fun heightBucket(heightCm: Int): String = when {
        heightCm < 160 -> "under_160"
        heightCm < 170 -> "160_170"
        heightCm < 180 -> "170_180"
        heightCm < 190 -> "180_190"
        else -> "190_plus"
    }

    private fun weightBucket(weightKg: Double): String = when {
        weightKg < 55 -> "under_55"
        weightKg < 70 -> "55_70"
        weightKg < 85 -> "70_85"
        weightKg < 100 -> "85_100"
        else -> "100_plus"
    }
}


