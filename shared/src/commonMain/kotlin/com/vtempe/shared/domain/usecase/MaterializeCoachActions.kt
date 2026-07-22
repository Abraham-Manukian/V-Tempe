package com.vtempe.shared.domain.usecase

import com.vtempe.shared.domain.model.Profile
import com.vtempe.shared.domain.model.TrainingPlan
import com.vtempe.shared.domain.repository.AdviceRepository
import com.vtempe.shared.domain.repository.CoachAction
import com.vtempe.shared.domain.repository.CoachActionType
import com.vtempe.shared.domain.repository.CoachResponse
import com.vtempe.shared.domain.repository.NutritionRepository
import com.vtempe.shared.domain.repository.ProfileRepository
import com.vtempe.shared.domain.repository.TrainingRepository
import kotlinx.coroutines.flow.first

/**
 * Materializes [CoachResponse.actions] into concrete domain objects:
 * fetches or generates training/nutrition plans and sleep advice based on
 * the actions the AI coach requested.
 *
 * Extracted from [AskAiTrainer] to give each class a single responsibility.
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

        // Note: actions may be empty even when the AI inlined a plan directly in the response
        // (e.g. "translate my meal plan to Russian" isn't a REBUILD_NUTRITION_PLAN action, it's
        // just a nutritionPlan object on the response) — the persistence below must still run
        // in that case, so there is no early return here.
        response.actions.forEach { action ->
            when (action.type) {
                CoachActionType.SHOW_CURRENT_WORKOUT -> {
                    if (trainingPlan == null) {
                        trainingPlan = currentOrGeneratedTrainingPlan(effectiveProfile, action.weekIndex ?: 0)
                    }
                }

                CoachActionType.REPLACE_EXERCISE -> {
                    val basePlan = trainingPlan
                        ?: currentOrGeneratedTrainingPlan(effectiveProfile, action.weekIndex ?: 0)
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
            }
        }

        // Persist whatever was materialized (covers inline AI plans and REPLACE_EXERCISE results)
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

    private fun replaceExerciseInPlan(
        plan: TrainingPlan,
        action: CoachAction
    ): TrainingPlan {
        val targetExerciseId = action.targetExerciseId?.trim()?.lowercase()
            ?.takeIf { it.isNotEmpty() } ?: return plan
        val replacementExerciseId = action.replacementExerciseId?.trim()?.lowercase()
            ?.takeIf { it.isNotEmpty() } ?: return plan
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
        "pushup", "pullup", "dip", "plank", "run" -> null
        else -> currentWeightKg
    }
}
