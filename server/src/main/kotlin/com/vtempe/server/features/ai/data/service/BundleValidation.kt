package com.vtempe.server.features.ai.data.service

import com.vtempe.server.features.ai.data.service.split.TrainingSplitPlanner
import com.vtempe.server.features.ai.domain.port.ExerciseCatalog
import com.vtempe.server.features.ai.domain.port.TrainingPlanResolver
import com.vtempe.server.shared.dto.bootstrap.AiBootstrapResponse
import com.vtempe.server.shared.dto.profile.AiProfile
import com.vtempe.server.shared.dto.training.AiTrainingResponse
import java.util.Locale

/**
 * Coach-bundle validation — extracted out of AiService (item A5, God object). Mechanical
 * extraction, no behavior change: same logic, dependencies (exerciseCatalog/
 * trainingPlanResolver) passed explicitly instead of read off AiService's instance fields.
 */

/**
 * Checks that each exercise in the raw AI response belongs to the expected movement pattern
 * for that skeleton slot. Returns specific, actionable error messages fed back to the LLM
 * so it can self-correct. Normalization still acts as a safety net after all retries.
 */
internal fun validateSkeletonCompliance(
    exerciseCatalog: ExerciseCatalog,
    trainingPlanResolver: TrainingPlanResolver,
    plan: AiTrainingResponse,
    profile: AiProfile,
    weekIndex: Int
): List<String> {
    val trainingDays = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
        .filter { profile.weeklySchedule[it] == true }
    if (trainingDays.isEmpty()) return emptyList()
    val skeletons = runCatching {
        TrainingSplitPlanner.build(
            trainingDays        = trainingDays,
            focusRaw            = profile.trainingFocus,
            goalRaw             = profile.goal,
            splitPreferenceRaw  = profile.splitPreference,
            experienceLevel     = profile.experienceLevel,
            age                 = profile.age,
            sexRaw              = profile.sex,
            lifestyleRaw        = profile.lifestyleActivity,
            injuries            = profile.injuries,
            sessionDurationMins = profile.sessionDurationMins,
            weekIndex           = weekIndex
        )
    }.getOrDefault(emptyList())
    if (skeletons.isEmpty()) return emptyList()

    val mode = trainingPlanResolver.resolveMode(profile.trainingMode, profile.equipment)
    val equipment = trainingPlanResolver.normalizeEquipment(profile.equipment)
    val errors = mutableListOf<String>()

    plan.workouts.forEachIndexed { wi, workout ->
        val skeleton = skeletons.getOrNull(wi) ?: return@forEachIndexed
        val sessionLabel = skeleton.label.let {
            val dash = it.indexOf(" — "); if (dash >= 0) it.substring(dash + 3) else it
        }
        workout.sets.forEachIndexed { si, set ->
            val expectedSlot = skeleton.slots.getOrNull(si) ?: return@forEachIndexed
            val token = normalizeExerciseToken(set.exerciseId)
            // Pattern tokens (pattern:*) are allowed — resolver picks the exercise
            if (token.startsWith("pattern:")) return@forEachIndexed
            val catalogItem = exerciseCatalog.findByIdOrAlias(token) ?: return@forEachIndexed
            if (catalogItem.primaryPattern != expectedSlot.pattern) {
                val examples = exerciseCatalog.candidatesFor(expectedSlot.pattern, mode, equipment)
                    .take(3).joinToString(", ") { it.id }
                errors += "workout[$wi] ($sessionLabel) slot ${si + 1}: " +
                    "expected ${expectedSlot.pattern.id} (${expectedSlot.pattern.promptDescription}) " +
                    "but got '${catalogItem.id}' which is ${catalogItem.primaryPattern.id}. " +
                    "Use one of: $examples"
            }
        }
    }
    return errors
}

internal fun validateBundle(
    exerciseCatalog: ExerciseCatalog,
    bundle: AiBootstrapResponse,
    profile: AiProfile,
    locale: Locale
): List<String> {
    val errors = mutableListOf<String>()

    val training = bundle.trainingPlan
    if (training == null) {
        errors += "trainingPlan was null"
    } else {
        validateTrainingPlan(training, exerciseCatalog, profile.injuries)?.let { errors += "trainingPlan: $it" }
    }

    val nutrition = bundle.nutritionPlan
    if (nutrition == null) {
        errors += "nutritionPlan was null"
    } else {
        errors += validateNutritionPlan(nutrition, profile, locale).map { "nutritionPlan: $it" }
    }

    val advice = bundle.sleepAdvice
    if (advice == null) {
        errors += "sleepAdvice was null"
    } else {
        validateSleepAdvice(advice)?.let { errors += "sleepAdvice: $it" }
    }

    return errors.distinct()
}
