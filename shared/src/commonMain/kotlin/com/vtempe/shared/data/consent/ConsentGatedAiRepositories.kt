package com.vtempe.shared.data.consent

import com.vtempe.shared.domain.consent.HealthDataConsentGate
import com.vtempe.shared.domain.model.Advice
import com.vtempe.shared.domain.model.NutritionPlan
import com.vtempe.shared.domain.model.Profile
import com.vtempe.shared.domain.model.TrainingPlan
import com.vtempe.shared.domain.repository.AiTrainerRepository
import com.vtempe.shared.domain.repository.ChatMessage
import com.vtempe.shared.domain.repository.ChatRepository
import com.vtempe.shared.domain.repository.CoachBundle
import com.vtempe.shared.domain.repository.CoachResponse
import com.vtempe.shared.domain.util.DataResult

/**
 * Every AI request carries the profile with health data (injuries, health notes, allergies,
 * sleep notes) to the server and on to the LLM provider abroad. These gates sit in front of the
 * network repositories, so no code path can send it without the user's consent. Consent is
 * re-read on each call: granting or withdrawing it in Settings applies to the very next request.
 *
 * A blocked call returns [DataResult.Reason.ConsentRequired]; callers already treat a failure as
 * "AI unavailable" (offline plans, error bubble), so the app keeps working without the AI.
 */
internal val consentRequiredFailure = DataResult.Failure(
    reason = DataResult.Reason.ConsentRequired,
    message = "Health data consent is required for AI features"
)

class ConsentGatedAiTrainerRepository(
    private val delegate: AiTrainerRepository,
    private val consent: HealthDataConsentGate
) : AiTrainerRepository {

    override suspend fun generateTrainingPlan(profile: Profile, weekIndex: Int): DataResult<TrainingPlan> =
        if (consent.isHealthDataConsentGranted()) delegate.generateTrainingPlan(profile, weekIndex) else consentRequiredFailure

    override suspend fun generateNutritionPlan(profile: Profile, weekIndex: Int): DataResult<NutritionPlan> =
        if (consent.isHealthDataConsentGranted()) delegate.generateNutritionPlan(profile, weekIndex) else consentRequiredFailure

    override suspend fun getSleepAdvice(profile: Profile): DataResult<Advice> =
        if (consent.isHealthDataConsentGranted()) delegate.getSleepAdvice(profile) else consentRequiredFailure

    override suspend fun bootstrap(profile: Profile, weekIndex: Int): DataResult<CoachBundle> =
        if (consent.isHealthDataConsentGranted()) delegate.bootstrap(profile, weekIndex) else consentRequiredFailure
}

class ConsentGatedChatRepository(
    private val delegate: ChatRepository,
    private val consent: HealthDataConsentGate
) : ChatRepository {

    override suspend fun send(
        profile: Profile,
        history: List<ChatMessage>,
        userMessage: String,
        locale: String?,
        currentTrainingPlan: TrainingPlan?,
        currentNutritionPlan: NutritionPlan?
    ): DataResult<CoachResponse> =
        if (consent.isHealthDataConsentGranted()) {
            delegate.send(profile, history, userMessage, locale, currentTrainingPlan, currentNutritionPlan)
        } else {
            consentRequiredFailure
        }
}
