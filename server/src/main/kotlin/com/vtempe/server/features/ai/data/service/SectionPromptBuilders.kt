package com.vtempe.server.features.ai.data.service

import com.vtempe.server.features.ai.data.service.split.TrainingSplitPlanner
import com.vtempe.server.features.ai.domain.port.ExerciseCatalog
import com.vtempe.server.features.ai.domain.port.TrainingPlanResolver
import com.vtempe.server.shared.dto.profile.AiProfile
import java.time.LocalDate
import java.util.Locale
import kotlinx.serialization.json.Json

/**
 * Prompt builders for the decomposed (per-section) generation path — extracted out of
 * AiService (item A5, God object) alongside CoachBundlePromptBuilder.kt for the monolithic
 * bundle path. Mechanical extraction, no behavior change: same logic, params passed
 * explicitly instead of read off AiService's instance fields.
 */

internal fun buildTrainingSectionPrompt(
    json: Json,
    exerciseCatalog: ExerciseCatalog,
    trainingPlanResolver: TrainingPlanResolver,
    profile: AiProfile,
    weekIndex: Int,
    locale: Locale,
    localeTag: String,
    measurementSystem: String,
    weightUnit: String
): String {
    val languageDisplay = locale.getDisplayLanguage(locale).ifBlank { locale.language.ifBlank { "English" } }
    val profileJson = json.encodeToString(AiProfile.serializer(), profile)
    val trainingResolverDescriptions = buildTrainingResolverDescriptions(
        exerciseCatalog = exerciseCatalog,
        trainingPlanResolver = trainingPlanResolver,
        trainingModeRaw = profile.trainingMode,
        equipment = profile.equipment
    )
    val today = LocalDate.now()
    val workoutDates = computeWorkoutDatesForWeek(profile.weeklySchedule, today)

    val trainingDays = if (workoutDates.isNotEmpty()) workoutDates
        else listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun").filter { profile.weeklySchedule[it] == true }
    val skeletons = TrainingSplitPlanner.build(
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
        weekIndex           = weekIndex,
        hasHistory          = profile.recentWorkouts.isNotEmpty()
    )
    val resolvedExercises = skeletons.mapIndexed { si, skeleton ->
        val used = mutableSetOf<String>()
        skeleton.slots.mapIndexed { j, slot ->
            val id = trainingPlanResolver.resolveExerciseId(
                rawToken            = slot.pattern.token,
                trainingModeRaw     = profile.trainingMode,
                equipment           = profile.equipment,
                usedExerciseIds     = used,
                rotationSeed        = si * 31 + j,
                userExperienceLevel = profile.experienceLevel
            )
            if (id != null) used += id
            id
        }
    }

    return buildString {
        appendLine("You are an elite strength coach.")
        appendLine("User locale: $languageDisplay ($localeTag). Reply with JSON only.")
        appendLine("TODAY'S DATE: $today — use this as the reference.")
        appendLine("Measurement system: $measurementSystem. Use $weightUnit for load.")
        appendLine("If PROFILE JSON contains recentWorkouts, use it to progress, hold, or regress load, volume, and exercise difficulty.")
        if (workoutDates.isNotEmpty()) {
            appendLine("MANDATORY workout dates — use EXACTLY these ISO dates, one workout per date, no other dates allowed:")
            appendLine("  ${workoutDates.joinToString(", ")}")
        } else {
            appendLine("Plan for weekIndex=$weekIndex starting from $today. All workout dates must be >= $today.")
        }
        appendLine("Return ONLY this JSON schema:")
        appendLine("{\"weekIndex\": Int, \"workouts\": [{\"id\": String, \"label\": String, \"date\": \"YYYY-MM-DD\", \"sets\": [{\"exerciseId\": String, \"reps\": Int, \"weightKg\": Double?, \"rpe\": Double?}]}]}")
        appendLine()
        appendLine(TrainingSplitPlanner.renderPromptBlock(skeletons, resolvedExercises))
        appendLine()
        appendLine("DURATION EXERCISES — use `reps` field for time, NOT repetition count:")
        appendLine("- plank, side_plank, wall_sit, l_sit, hollow_body, mountain_climber: `reps` = seconds (e.g. reps=30 means 30s hold). Range: ${SECONDS_RANGE.first}–${SECONDS_RANGE.last}.")
        appendLine("- bike/stationary_bike/cycling, rowing_machine: `reps` = MINUTES, not seconds. Range: ${MINUTES_RANGE.first}–${MINUTES_RANGE.last}.")
        appendLine("- run/jog/treadmill: `reps` = minutes. Range: ${MINUTES_RANGE.first}–${MINUTES_RANGE.last}.")
        appendLine("- NEVER set reps=8 for a plank. Set reps=30 for a 30-second plank.")
        appendLine("- For all duration exercises: set weightKg = null.")
        appendLine()
        appendLine("WEIGHT ASSIGNMENT RULES (CRITICAL):")
        appendLine("- Bodyweight exercises (pullup, chin_up, wide_pullup, pushup, dip, plank, lunge, nordic_curl, muscle_up): set weightKg = null.")
        appendLine("- Barbell exercises (squat, bench_press, deadlift, rdl, barbell_row, overhead_press): assign realistic starting weights.")
        appendLine("  Beginner male: ~60kg squat, ~50kg bench, ~70kg deadlift. Scale ±20% per experience level.")
        appendLine("  Beginner female: ~30kg squat, ~25kg bench, ~40kg deadlift.")
        appendLine("- Dumbbell exercises: use per-dumbbell weight (e.g. 15.0 for 15kg dumbbells).")
        appendLine("- NEVER assign the same weightKg to a barbell compound AND a bodyweight exercise.")
        appendLine()
        appendLine(trainingResolverDescriptions)
        appendLine("Max $MaxWorkoutsPerPlan workouts, max $MaxSetsPerWorkout sets per workout, no duplicate workout IDs.")
        append(untrustedDataBlock("PROFILE JSON", profileJson))
    }
}

internal fun buildNutritionSectionPrompt(
    json: Json,
    profile: AiProfile,
    weekIndex: Int,
    locale: Locale,
    localeTag: String,
    measurementSystem: String,
    weightUnit: String
): String {
    val languageDisplay = locale.getDisplayLanguage(locale).ifBlank { locale.language.ifBlank { "English" } }
    val profileJson = json.encodeToString(AiProfile.serializer(), profile)
    val restrictionsSummary = nutritionRestrictionsPrompt(profile)
    val targets = computeTargetNutrition(profile)
    return buildString {
        appendLine("You are an elite sports nutritionist.")
        appendLine("User locale: $languageDisplay ($localeTag). Reply in this language.")
        appendLine("Measurement system: $measurementSystem. Bodyweight unit: $weightUnit.")
        appendLine()
        appendLine(restrictionsSummary)
        appendLine()
        appendLine("PRE-COMPUTED TARGETS — use EXACTLY these values, do NOT recalculate:")
        appendLine("  Daily kcal: ${targets.kcal}  |  Protein: ${targets.proteinG}g  |  Fat: ${targets.fatG}g  |  Carbs: ${targets.carbsG}g")
        appendLine("The sum of all meals every day MUST be within ±5% of these targets.")
        if (profile.dietaryPreferences.isNotEmpty()) {
            appendLine("PREFERRED FOODS (raw user text, include these in meals where possible, they are likes — not restrictions): ${profile.dietaryPreferences.joinToString(", ") { sanitizeInlineUserText(it) }}")
        }
        appendLine()
        appendLine("Return ONLY this JSON schema:")
        appendLine("{\"weekIndex\": Int, \"mealsByDay\": {\"Mon\":[{\"name\": String, \"ingredients\": [String], \"recipe\": String, \"allergenTags\": [String], \"kcal\": Int, \"macros\": {\"proteinGrams\": Int, \"fatGrams\": Int, \"carbsGrams\": Int, \"kcal\": Int}}], \"Tue\": [...], \"Wed\": [...], \"Thu\": [...], \"Fri\": [...], \"Sat\": [...], \"Sun\": [...]}, \"shoppingList\": [String]}")
        appendLine("Nutrition hard rules:")
        appendLine("- Cover Mon..Sun with required day keys.")
        appendLine("- Meals/day by goal: lose -> 3-4, maintain -> 3-5, gain -> 4-6.")
        appendLine("- Each meal needs integer macros and kcal aligned with 4*protein + 4*carbs + 9*fat (+/-20).")
        appendLine("- Avoid duplicate meals within the same day.")
        appendLine("- INGREDIENTS: always include quantity + unit for each ingredient (e.g. \"150г гречки\", \"200мл кефира\", \"2 яйца\"). Never list bare ingredient names without amounts.")
        appendLine("- RECIPE: provide a short 2-4 step cooking instruction in $languageDisplay. Steps concise — one action each. Do NOT include nutritional commentary.")
        appendLine("- Example meal: {\"name\":\"Овсянка\",\"ingredients\":[\"150г овсяных хлопьев\",\"250мл молока\"],\"recipe\":\"1. Залей хлопья молоком. 2. Вари 5 мин.\",\"kcal\":360,\"macros\":{\"proteinGrams\":10,\"fatGrams\":6,\"carbsGrams\":64,\"kcal\":358}}")
        append(untrustedDataBlock("PROFILE JSON", profileJson))
        appendLine("weekIndex=$weekIndex")
    }
}

internal fun buildAdviceSectionPrompt(
    json: Json,
    profile: AiProfile,
    locale: Locale,
    localeTag: String
): String {
    val languageDisplay = locale.getDisplayLanguage(locale).ifBlank { locale.language.ifBlank { "English" } }
    val profileJson = json.encodeToString(AiProfile.serializer(), profile)
    return buildString {
        appendLine("You are a recovery and sleep coach.")
        appendLine("User locale: $languageDisplay ($localeTag). Reply in this language.")
        appendLine("Return ONLY this JSON schema:")
        appendLine("{\"messages\": [String], \"disclaimer\": String}")
        appendLine("Provide 5-7 concise, practical tips.")
        append(untrustedDataBlock("PROFILE JSON", profileJson))
    }
}
