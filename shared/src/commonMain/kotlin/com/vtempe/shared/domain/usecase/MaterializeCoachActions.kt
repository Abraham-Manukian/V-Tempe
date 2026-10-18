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
    /** [currentWeek]: the week the user is in, for actions that name none — never the first week. */
    suspend operator fun invoke(
        baseProfile: Profile,
        response: CoachResponse,
        currentWeek: Int,
    ): CoachResponse {
        var effectiveProfile = baseProfile
        var trainingPlan = response.trainingPlan
        var nutritionPlan = response.nutritionPlan
        var sleepAdvice = response.sleepAdvice

        response.actions.forEach { action ->
            when (action.type) {
                CoachActionType.SHOW_CURRENT_WORKOUT -> {
                    if (trainingPlan == null) {
                        trainingPlan = currentOrGeneratedTrainingPlan(effectiveProfile, weekFor(action, currentWeek))
                    }
                }

                CoachActionType.REBUILD_TRAINING_PLAN -> {
                    if (trainingPlan == null) {
                        val profileForAction = action.trainingMode
                            ?.let { mode -> effectiveProfile.copy(trainingMode = mode) }
                            ?: effectiveProfile
                        trainingPlan = trainingRepository.generatePlan(profileForAction, weekFor(action, currentWeek))
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
                        trainingPlan = trainingRepository.generatePlan(effectiveProfile, weekFor(action, currentWeek))
                    }
                }

                CoachActionType.REBUILD_NUTRITION_PLAN -> {
                    if (nutritionPlan == null) {
                        nutritionPlan = nutritionRepository.generatePlan(effectiveProfile, weekFor(action, currentWeek))
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
        // Never store an empty record: it would count as advice and block the automatic fetch.
        sleepAdvice?.takeIf { it.messages.isNotEmpty() }?.let { adviceRepository.saveAdvice("sleep", it) }

        return response.copy(
            trainingPlan = trainingPlan,
            nutritionPlan = nutritionPlan,
            sleepAdvice = sleepAdvice
        )
    }

    /** The week an action rebuilds: the model may name one, but only this week or the next is
     *  accepted — a stale or invented index would rebuild a week the user never sees. */
    private fun weekFor(action: CoachAction, currentWeek: Int): Int =
        action.weekIndex?.takeIf { it in currentWeek..currentWeek + 1 } ?: currentWeek

    private suspend fun currentOrGeneratedTrainingPlan(
        profile: Profile,
        weekIndex: Int
    ): TrainingPlan {
        // Only that week's workouts: the plan is saved back under [weekIndex], and all weeks would
        // pull earlier workouts into it.
        val currentWorkouts = trainingRepository.observeWorkoutsByWeek(weekIndex).first()
        return if (currentWorkouts.isNotEmpty()) {
            TrainingPlan(weekIndex = weekIndex, workouts = currentWorkouts.sortedBy { it.date })
        } else {
            trainingRepository.generatePlan(profile, weekIndex)
        }
    }
}
