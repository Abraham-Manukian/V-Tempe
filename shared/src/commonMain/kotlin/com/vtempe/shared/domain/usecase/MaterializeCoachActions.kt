package com.vtempe.shared.domain.usecase

import com.vtempe.shared.domain.model.Profile
import com.vtempe.shared.domain.model.TrainingPlan
import com.vtempe.shared.domain.repository.AdviceRepository
import com.vtempe.shared.domain.repository.CoachActionType
import com.vtempe.shared.domain.repository.CoachResponse
import com.vtempe.shared.domain.repository.NutritionRepository
import com.vtempe.shared.domain.repository.ProfileRepository
import com.vtempe.shared.domain.repository.TrainingRepository
import kotlinx.coroutines.flow.first

/**
 * Materializes a [CoachResponse] into persisted plans.
 *
 * Two kinds of change arrive from the coach:
 *  - Targeted EDITS (swap an exercise, change a weight, replace an ingredient) are applied and
 *    validated entirely SERVER-SIDE now; they come back as a fully-formed trainingPlan /
 *    nutritionPlan on the response. This class just saves whatever plan is present — it must NOT
 *    try to re-derive or second-guess the edit (that older client-side "replace_exercise" path
 *    fought the server's own logic and silently discarded edits).
 *  - WHOLE-PLAN regeneration ("rebuild my plan", "switch to home workouts") still arrives as an
 *    action, because it needs a fresh generatePlan() call the server can't do inside a chat turn.
 *
 * So the only work left here is: honor the regeneration/show actions, then persist whatever plans
 * ended up on the response.
 */
class MaterializeCoachActions(
    private val profileRepository: ProfileRepository,
    private val trainingRepository: TrainingRepository,
    private val nutritionRepository: NutritionRepository,
    private val adviceRepository: AdviceRepository
) {
    suspend operator fun invoke(
        baseProfile: Profile,
        response: CoachResponse
    ): CoachResponse {
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

                CoachActionType.REBUILD_TRAINING_PLAN -> {
                    if (trainingPlan == null) {
                        val profileForAction = action.trainingMode
                            ?.let { mode -> effectiveProfile.copy(trainingMode = mode) }
                            ?: effectiveProfile
                        trainingPlan = trainingRepository.generatePlan(profileForAction, action.weekIndex ?: 0)
                    }
                }

                CoachActionType.SWITCH_TRAINING_MODE -> {
                    val targetMode = action.trainingMode?.trim()?.lowercase()
                        ?.takeIf { it.isNotEmpty() } ?: return@forEach
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

                CoachActionType.REPLACE_EXERCISE -> {
                    // Legacy: exercise swaps are now applied and validated server-side and arrive as a
                    // full trainingPlan on the response. A bare replace_exercise action with no inline
                    // plan is intentionally a no-op — the client must never re-run the swap itself.
                }
            }
        }

        // Persist whatever ended up materialized. A null plan means "no change to this domain" —
        // the server only attaches a plan when it actually differs from what the user had.
        trainingPlan?.let { trainingRepository.savePlan(it) }
        nutritionPlan?.let { nutritionRepository.savePlan(it) }
        sleepAdvice?.let { adviceRepository.saveAdvice("sleep", it) }

        return response.copy(
            trainingPlan = trainingPlan,
            nutritionPlan = nutritionPlan,
            sleepAdvice = sleepAdvice
        )
    }

    private suspend fun currentOrGeneratedTrainingPlan(
        profile: Profile,
        weekIndex: Int
    ): TrainingPlan {
        val currentWorkouts = trainingRepository.observeWorkouts().first()
        return if (currentWorkouts.isNotEmpty()) {
            TrainingPlan(weekIndex = weekIndex, workouts = currentWorkouts.sortedBy { it.date })
        } else {
            trainingRepository.generatePlan(profile, weekIndex)
        }
    }
}
