package com.vtempe.shared.domain.usecase

import com.vtempe.shared.domain.repository.ChatRepository
import com.vtempe.shared.domain.repository.ChatMessage
import com.vtempe.shared.domain.repository.CoachCacheRepository
import com.vtempe.shared.domain.repository.CoachResponse
import com.vtempe.shared.domain.repository.LanguagePreferences
import com.vtempe.shared.domain.repository.ProfileRepository
import com.vtempe.shared.domain.util.CoachDataFreshness
import com.vtempe.shared.domain.util.DataResult
import io.github.aakira.napier.Napier
import kotlinx.datetime.Clock

class AskAiTrainer(
    private val profileRepository: ProfileRepository,
    private val chatRepository: ChatRepository,
    private val languagePrefs: LanguagePreferences,
    private val materializeCoachActions: MaterializeCoachActions,
    private val coachCache: CoachCacheRepository
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

        val locale = localeOverride ?: languagePrefs.getLanguageTag()
        val result = chatRepository.send(profile, history, userMessage, locale)

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
