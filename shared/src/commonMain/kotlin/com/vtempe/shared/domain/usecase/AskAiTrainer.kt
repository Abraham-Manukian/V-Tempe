package com.vtempe.shared.domain.usecase

import com.vtempe.shared.domain.model.Advice
import com.vtempe.shared.data.repo.AiResponseCache
import com.vtempe.shared.domain.repository.AdviceRepository
import com.vtempe.shared.domain.repository.CoachAction
import com.vtempe.shared.domain.repository.CoachActionType
import com.vtempe.shared.domain.repository.ChatMessage
import com.vtempe.shared.domain.repository.ChatRepository
import com.vtempe.shared.domain.repository.CoachResponse
import com.vtempe.shared.domain.repository.NutritionRepository
import com.vtempe.shared.domain.repository.PreferencesRepository
import com.vtempe.shared.domain.repository.ProfileRepository
import com.vtempe.shared.domain.repository.TrainingRepository
import com.vtempe.shared.domain.util.DataResult
import com.vtempe.shared.domain.util.CoachDataFreshness
import io.github.aakira.napier.Napier
import kotlinx.datetime.Clock
import kotlinx.coroutines.flow.first

class AskAiTrainer(
    private val profileRepository: ProfileRepository,
    private val chatRepository: ChatRepository,
    private val preferencesRepository: PreferencesRepository,
    private val trainingRepository: TrainingRepository,
    private val nutritionRepository: NutritionRepository,
    private val adviceRepository: AdviceRepository,
    private val aiResponseCache: AiResponseCache
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

        val locale = localeOverride ?: preferencesRepository.getLanguageTag()
        val result = chatRepository.send(profile, history, userMessage, locale)

        return when (result) {
            is DataResult.Success -> {
                val materialized = materializeActions(
                    baseProfile = profile,
                    response = result.data
                )
                val hasCoachUpdates =
                    materialized.trainingPlan != null ||
                        materialized.nutritionPlan != null ||
                        materialized.sleepAdvice != null

                materialized.trainingPlan?.let { trainingRepository.savePlan(it) }
                materialized.nutritionPlan?.let { nutritionRepository.savePlan(it) }
                materialized.sleepAdvice?.let { adviceRepository.saveAdvice("sleep", it) }

                if (hasCoachUpdates) {
                    aiResponseCache.markBundleFresh(
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

    private suspend fun materializeActions(
        baseProfile: com.vtempe.shared.domain.model.Profile,
        response: CoachResponse
    ): CoachResponse {
        if (response.actions.isEmpty()) return response

        var effectiveProfile = baseProfile
        var trainingPlan = response.trainingPlan
        var nutritionPlan = response.nutritionPlan
        var sleepAdvice = response.sleepAdvice

        response.actions.forEach { action ->
            when (action.type) {
                CoachActionType.SHOW_CURRENT_WORKOUT -> {
                    if (trainingPlan == null) {
                        trainingPlan = currentOrGeneratedTrainingPlan(effectiveProfile, action.weekIndex ?: 0)
                    }
                }

                CoachActionType.REPLACE_EXERCISE -> {
                    val basePlan = trainingPlan ?: currentOrGeneratedTrainingPlan(effectiveProfile, action.weekIndex ?: 0)
                    trainingPlan = replaceExerciseInPlan(basePlan, action)
                }

                CoachActionType.REBUILD_TRAINING_PLAN -> {
                    if (trainingPlan == null) {
                        val profileForAction = action.trainingMode
                            ?.let { mode -> effectiveProfile.copy(trainingMode = mode) }
                            ?: effectiveProfile
                        trainingPlan = trainingRepository.generatePlan(profileForAction, action.weekIndex ?: 0)
                    }
                }

                CoachActionType.SWITCH_TRAINING_MODE -> {
                    val targetMode = action.trainingMode?.trim()?.lowercase()?.takeIf { it.isNotEmpty() } ?: return@forEach
                    if (!effectiveProfile.trainingMode.equals(targetMode, ignoreCase = true)) {
                        effectiveProfile = effectiveProfile.copy(trainingMode = targetMode)
                        profileRepository.upsertProfile(effectiveProfile)
                    }
                    if (trainingPlan == null) {
                        trainingPlan = trainingRepository.generatePlan(effectiveProfile, action.weekIndex ?: 0)
                    }
                }

                CoachActionType.REBUILD_NUTRITION_PLAN -> {
                    if (nutritionPlan == null) {
                        nutritionPlan = nutritionRepository.generatePlan(effectiveProfile, action.weekIndex ?: 0)
                    }
                }

                CoachActionType.REFRESH_SLEEP_ADVICE -> {
                    if (sleepAdvice == null) {
                        sleepAdvice = adviceRepository.getAdvice(effectiveProfile, mapOf("topic" to "sleep"))
                    }
                }
            }
        }

        return response.copy(
            trainingPlan = trainingPlan,
            nutritionPlan = nutritionPlan,
            sleepAdvice = sleepAdvice
        )
    }

    private suspend fun currentOrGeneratedTrainingPlan(
        profile: com.vtempe.shared.domain.model.Profile,
        weekIndex: Int
    ): com.vtempe.shared.domain.model.TrainingPlan {
        val currentWorkouts = trainingRepository.observeWorkouts().first()
        return if (currentWorkouts.isNotEmpty()) {
            com.vtempe.shared.domain.model.TrainingPlan(
                weekIndex = weekIndex,
                workouts = currentWorkouts.sortedBy { it.date }
            )
        } else {
            trainingRepository.generatePlan(profile, weekIndex)
        }
    }

    private fun replaceExerciseInPlan(
        plan: com.vtempe.shared.domain.model.TrainingPlan,
        action: CoachAction
    ): com.vtempe.shared.domain.model.TrainingPlan {
        val targetExerciseId = action.targetExerciseId?.trim()?.lowercase()?.takeIf { it.isNotEmpty() } ?: return plan
        val replacementExerciseId = action.replacementExerciseId?.trim()?.lowercase()?.takeIf { it.isNotEmpty() } ?: return plan
        if (targetExerciseId == replacementExerciseId) return plan

        val scopedWorkoutId = action.workoutId?.trim()?.takeIf { it.isNotEmpty() }
        var replacedAny = false

        val updatedWorkouts = plan.workouts.map { workout ->
            if (scopedWorkoutId != null && workout.id != scopedWorkoutId) return@map workout

            val containsTarget = workout.sets.any { it.exerciseId.equals(targetExerciseId, ignoreCase = true) }
            if (!containsTarget) return@map workout

            replacedAny = true
            workout.copy(
                sets = workout.sets.map { set ->
                    if (!set.exerciseId.equals(targetExerciseId, ignoreCase = true)) {
                        set
                    } else {
                        set.copy(
                            exerciseId = replacementExerciseId,
                            weightKg = adjustReplacementWeight(replacementExerciseId, set.weightKg)
                        )
                    }
                }
            )
        }

        return if (replacedAny) plan.copy(workouts = updatedWorkouts) else plan
    }

    private fun adjustReplacementWeight(
        replacementExerciseId: String,
        currentWeightKg: Double?
    ): Double? = when (replacementExerciseId.lowercase()) {
        "pushup", "pullup", "dip", "plank", "run", "yoga" -> null
        else -> currentWeightKg
    }
}



