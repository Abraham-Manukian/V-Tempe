package com.vtempe.shared.domain.usecase

import com.vtempe.shared.domain.repository.ChatRepository
import com.vtempe.shared.domain.repository.ChatMessage
import com.vtempe.shared.domain.repository.CoachCacheRepository
import com.vtempe.shared.domain.repository.CoachResponse
import com.vtempe.shared.domain.repository.LanguagePreferences
import com.vtempe.shared.domain.repository.NutritionRepository
import com.vtempe.shared.domain.repository.ProfileRepository
import com.vtempe.shared.domain.repository.TrainingRepository
import com.vtempe.shared.domain.util.CoachDataFreshness
import com.vtempe.shared.domain.util.CoachSchedule
import com.vtempe.shared.domain.util.DataResult
import io.github.aakira.napier.Napier
import kotlinx.coroutines.flow.first
import kotlinx.datetime.Clock

class AskAiTrainer(
    private val profileRepository: ProfileRepository,
    private val chatRepository: ChatRepository,
    private val languagePrefs: LanguagePreferences,
    private val materializeCoachActions: MaterializeCoachActions,
    private val coachCache: CoachCacheRepository,
    private val trainingRepository: TrainingRepository,
    private val nutritionRepository: NutritionRepository,
) {
    suspend operator fun invoke(
        history: List<ChatMessage>,
        userMessage: String,
        localeOverride: String? = null
    ): DataResult<CoachResponse> {

        val profile = profileRepository.getProfile()
            ?: return DataResult.Failure(
                reason = DataResult.Reason.Unknown,
                message = "Profile required. Please complete onboarding first."
            )

        // Resolve current week index so we can fetch the right plan
        val epochMs = coachCache.planEpochDateMs()
        val weekIndex = if (epochMs != null) CoachSchedule.currentWeekIndex(epochMs) else 0

        // Fetch current training and nutrition plans — coach needs full context for precise edits
        val currentTrainingPlan = runCatching {
            trainingRepository.observeWorkoutsByWeek(weekIndex).first()
                .takeIf { it.isNotEmpty() }
                ?.let { workouts ->
                    com.vtempe.shared.domain.model.TrainingPlan(weekIndex = weekIndex, workouts = workouts)
                }
        }.getOrNull()

        val currentNutritionPlan = runCatching {
            nutritionRepository.observePlan().first()
        }.getOrNull()

        val locale = localeOverride ?: languagePrefs.getLanguageTag()
        val result = chatRepository.send(
            profile = profile,
            history = history,
            userMessage = userMessage,
            locale = locale,
            currentTrainingPlan = currentTrainingPlan,
            currentNutritionPlan = currentNutritionPlan
        )

        return when (result) {
            is DataResult.Success -> {
                val materialized = materializeCoachActions(
                    baseProfile = profile,
                    response = result.data
                )
                val hasCoachUpdates =
                    materialized.trainingPlan != null ||
                        materialized.nutritionPlan != null ||
                        materialized.sleepAdvice != null

                if (hasCoachUpdates) {
                    coachCache.markBundleFresh(
                        version = CoachDataFreshness.SCHEMA_VERSION,
                        timestampMillis = Clock.System.now().toEpochMilliseconds()
                    )
                }
                DataResult.Success(
                    data = materialized,
                    fromCache = result.fromCache,
                    rawPayload = result.rawPayload
                )
            }

            is DataResult.Failure -> {
                Napier.w(
                    message = "Chat send failed: ${result.reason} ${result.message.orEmpty()}",
                    throwable = result.throwable
                )
                result
            }
        }
    }
}
