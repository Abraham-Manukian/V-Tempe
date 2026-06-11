package com.vtempe.server.features.ai.data.service

import com.vtempe.server.features.ai.domain.port.ExerciseCatalog
import com.vtempe.server.features.ai.domain.port.TrainingPlanResolver
import com.vtempe.server.shared.dto.bootstrap.AiBootstrapRequest
import com.vtempe.server.shared.dto.profile.AiProfile
import java.time.LocalDate
import java.util.Locale
import kotlinx.serialization.json.Json

internal fun buildBundlePrompt(
    json: Json,
    locale: Locale,
    measurementSystem: String,
    weightUnit: String,
    request: AiBootstrapRequest,
    exerciseCatalog: ExerciseCatalog,
    trainingPlanResolver: TrainingPlanResolver
): String {
    val languageDisplay = locale.getDisplayLanguage(locale).ifBlank { locale.language.ifBlank { "English" } }
    val profileJson = json.encodeToString(AiProfile.serializer(), request.profile)
    val preferencesSummary = buildPreferencesSummary(request.profile)
    val restrictionsSummary = nutritionRestrictionsPrompt(request.profile)
    val trainingResolverPrompt = buildTrainingResolverPrompt(
        exerciseCatalog = exerciseCatalog,
        trainingPlanResolver = trainingPlanResolver,
        trainingModeRaw = request.profile.trainingMode,
        equipment = request.profile.equipment
    )
    val today = LocalDate.now()
    val todayIso = today.toString() // YYYY-MM-DD

    return buildString {
        appendLine("You are an elite strength coach, nutritionist, and recovery specialist guiding this athlete long-term.")
        appendLine("User language: $languageDisplay. Reply strictly in this language and measurement system.")
        appendLine("TODAY'S DATE: $todayIso — use this as the reference for all workout dates.")
        appendLine("Return ONLY a single JSON object and nothing else (no markdown, introductions, or explanations).")
        appendLine("Do not escape quotes (no \\\" sequences). Output must be valid JSON.")
        appendLine()
        appendLine("PROFILE CONTEXT (JSON):")
        appendLine(profileJson)
        appendLine()
        appendLine("KEY FACTS ABOUT THE ATHLETE:")
        append(preferencesSummary)
        appendLine()
        val injuryPrompt = buildInjuryRestrictionsPrompt(request.profile.injuries)
        if (injuryPrompt.isNotBlank()) {
            appendLine(injuryPrompt)
            appendLine()
        }
        appendLine("NUTRITION RESTRICTIONS (NON-NEGOTIABLE — READ BEFORE GENERATING ANY MEAL):")
        appendLine(restrictionsSummary)
        appendLine()
        appendLine("RESPONSE SCHEMA (STRICT JSON):")
        appendLine("{\"trainingPlan\": {\"weekIndex\": Int, \"workouts\": [{\"id\": String, \"date\": \"YYYY-MM-DD\", \"sets\": [{\"exerciseId\": String, \"reps\": Int, \"weightKg\": Double?, \"rpe\": Double?}]}]},")
        appendLine(" \"nutritionPlan\": {\"weekIndex\": Int, \"mealsByDay\": {DayLabel: [{\"name\": String, \"ingredients\": [String], \"allergenTags\": [String], \"kcal\": Int, \"macros\": {\"proteinGrams\": Int, \"fatGrams\": Int, \"carbsGrams\": Int, \"kcal\": Int}}]}, \"shoppingList\": [String]},")
        appendLine(" \"sleepAdvice\": {\"messages\": [String], \"disclaimer\": String}}")
        appendLine()
        appendLine("VALID RESPONSE EXAMPLE (structure only — replace $todayIso with actual week dates, use real compliant ingredients):")
        appendLine("{\"trainingPlan\":{\"weekIndex\":0,\"workouts\":[{\"id\":\"w_0_0\",\"date\":\"$todayIso\",\"sets\":[{\"exerciseId\":\"pattern:knee_dominant\",\"reps\":8,\"weightKg\":60.0,\"rpe\":7.5}]}]},")
        appendLine("\"nutritionPlan\":{\"weekIndex\":0,\"mealsByDay\":{\"Mon\":[{\"name\":\"Oats with berries\",\"ingredients\":[\"rolled oats\",\"water\",\"berries\"],\"allergenTags\":[\"Gluten\"],\"kcal\":360,\"macros\":{\"proteinGrams\":10,\"fatGrams\":6,\"carbsGrams\":64,\"kcal\":358}}]},\"shoppingList\":[\"rolled oats\",\"berries\"]},")
        appendLine("\"sleepAdvice\":{\"messages\":[\"Go to bed and wake up at the same time every day, even on weekends.\",\"Avoid screens 60 minutes before sleep — blue light suppresses melatonin.\",\"Keep your bedroom cool (16-19 C) and dark for better sleep quality.\"],\"disclaimer\":\"Not medical advice\"}}")
        appendLine("")
        appendLine("")
        appendLine("{\"messages\":[\"\\u041b\\u043e\\u0436\\u0438\\u0442\\u0435\\u0441\u044c \\u0438 \\u043f\\u0440\u043e\u0441\u044b\u043f\u0430\\u0439\u0442\u0435\u0441\u044c \\u0432 \\u043e\u0434\u043d\u043e \\u0438 \\u0442\u043e \\u0436\\u0435 \\u0432\u0440\u0435\u043c\u044f.\"],\"disclaimer\":\"\\u0421\\u043e\\u0432\u0435\u0442\u044b \\u043d\\u0435 \\u0437\\u0430\u043c\u0435\u043d\u044f\\u044e\u0442 \\u0432\u0440\u0430\u0447\u0430. \\u041f\u0440\u0438 \\u043f\u0440\u043e\u0431\u043b\u0435\u043c\u0430\u0445 \\u043e\u0431\u0440\u0430\u0442\u0438\u0442\u0435\u0441\u044c \\u043a \\u0441\u043f\u0435\u0446\u0438\u0430\u043b\u0438\u0441\u0442\u0443.\"}")
        appendLine("All arrays must close properly. No missing commas, no trailing commas, no duplicated braces.")
        appendLine()
        val workoutDates = computeWorkoutDatesForWeek(request.profile.weeklySchedule, today)
        appendLine("TRAINING RULES:")
        val expLevel = request.profile.experienceLevel
        val maxDifficulty = (expLevel + 1).coerceAtMost(5)
        appendLine("- User experience level: $expLevel / 5. ONLY use exercises with difficulty ≤ $maxDifficulty.")
        appendLine("  Level 1 = beginner: stick to wall sits, pushups, goblet squats, lat pulldowns, cable rows.")
        appendLine("  Level 2 = novice: add squats, deadlifts, dumbbell rows, pulldowns, lunges.")
        appendLine("  Level 3 = intermediate: barbell lifts, pullups, dips, romanian deadlifts.")
        appendLine("  Level 4 = advanced: bulgarian split squats, single-leg deadlifts, wide pullups, nordic curls.")
        appendLine("  Level 5 = elite: pistol squats, muscle-ups, handstand pushups, L-sits, toes-to-bar.")
        appendLine("  NEVER assign pistol squats, muscle-ups, handstand pushups, or L-sits to level 1-3 users.")
        if (workoutDates.isNotEmpty()) {
            appendLine("- MANDATORY workout dates (do NOT deviate — use these exact ISO dates, one workout per date):")
            appendLine("  ${workoutDates.joinToString(", ")}")
            appendLine("  These were computed from the user's weekly schedule. Never schedule a workout on any other date.")
        } else {
            appendLine("- Use ISO dates (YYYY-MM-DD). Plan for week index ${request.weekIndex} starting from $todayIso.")
        }
        appendLine("- Measurement system: $measurementSystem. Weights must be in $weightUnit (use null for bodyweight).")
        appendLine("- Provide balanced push/pull/legs/core coverage. 4-6 exercises per workout, vary rep ranges 4-12, include RPE 6.5-9.0.")
        appendLine(trainingResolverPrompt)
        appendLine("- Every workout.id must be unique inside trainingPlan. Never repeat workout IDs.")
        appendLine("- Limit workouts to at most 5 in the plan and sets to at most 6 per workout to keep the JSON concise.")
        appendLine()
        val targets = computeTargetNutrition(request.profile)
        appendLine("NUTRITION RULES:")
        appendLine("- PRE-COMPUTED TARGETS FOR THIS ATHLETE (use exactly these values, do NOT recalculate):")
        appendLine("  Daily calories: ${targets.kcal} kcal")
        appendLine("  Protein: ${targets.proteinG} g/day  |  Fat: ${targets.fatG} g/day  |  Carbs: ${targets.carbsG} g/day")
        appendLine("  The sum of all meals for every day MUST be within ±5% of these targets.")
        appendLine("- Cover every day Mon..Sun. Pick meal frequency: lose weight → 3-4 meals/day, maintain → 3-5, gain → 4-6.")
        appendLine("- Distribute the daily totals proportionally across meals and keep integers.")
        appendLine("- The sum of meals for any day must stay within +/-5% of the goal-adjusted daily calories and macros you calculated.")
        appendLine("- Ingredients must be plain text strings (no numbering or markdown). Keep meal names varied, localized, and practical.")
        appendLine("- Never include ingredients that violate allergies/intolerances or restricted foods listed above.")
        appendLine("- Each meal MUST include integer macros {proteinGrams, fatGrams, carbsGrams, kcal}. No field may be null or omitted.")
        appendLine("- Example meal object (use restriction-compliant ingredients for the actual athlete): {\"name\":\"Power Oats\",\"ingredients\":[\"rolled oats\",\"water\",\"berries\"],\"kcal\":360,\"macros\":{\"proteinGrams\":10,\"fatGrams\":6,\"carbsGrams\":64,\"kcal\":358}}")
        appendLine("- Ensure macros.kcal equals proteinGrams*4 + carbsGrams*4 + fatGrams*9 (allowed difference +/-20 kcal). If it does not match, adjust kcal to satisfy the formula.")
        appendLine("- Do not include a shoppingList field; it will be computed downstream from the ingredients you provide.")
        appendLine("- Close each day array with ] before defining the next day. No nested day structures.")
        appendLine()
        appendLine("SLEEP ADVICE RULES:")
        appendLine("- Provide EXACTLY 4-6 concise, actionable SLEEP HYGIENE tips.")
        appendLine("- ONLY cover: sleep timing, bedtime routine, bedroom environment, wind-down habits, caffeine/screen cut-off, consistency of schedule.")
        appendLine("- DO NOT include nutrition tips, protein intake, workout scheduling, or general fitness advice in sleepAdvice — those belong in other fields.")
        appendLine("- Each message must be 1-2 sentences max, practical, and specific to sleep quality.")
        appendLine("- If locale is 'ru', write all messages in Russian. Otherwise write in English.")
        appendLine("- Disclaimer must clearly state the tips are informational and not medical advice.")
        appendLine()
        appendLine("GLOBAL VALIDATION RULES:")
        if (workoutDates.isNotEmpty()) {
            appendLine("- Workout dates must be EXACTLY: ${workoutDates.joinToString(", ")} — no other dates allowed.")
        } else {
            appendLine("- Training workout dates must be ISO (YYYY-MM-DD) and use week index ${request.weekIndex}.")
        }
        appendLine("- Day keys in mealsByDay must be exactly Mon,Tue,Wed,Thu,Fri,Sat,Sun in that order.")
        appendLine("- exerciseId values must stay within the resolver slot tokens declared above.")
        appendLine("- For every meal, kcal must follow 4*protein + 4*carbs + 9*fat within +/-20 as already stated.")
        appendLine("- Daily meal frequencies you output must respect the goal ranges listed above; reject plans that fall outside the permitted meal counts.")
        appendLine("- All dates in nutrition or training contexts must be valid calendar dates (ISO format).")
        appendLine("Ensure that every array (for example mealsByDay[Day]) closes with ] before the enclosing object } closes.")
        appendLine("Never output double }} or any sequence like }}} right before ]. Always end JSON with a single }.")
    }
}

internal data class NutritionTargets(val kcal: Int, val proteinG: Int, val fatG: Int, val carbsG: Int)

/**
 * Computes the exact ISO dates (YYYY-MM-DD) for workout days in the week that contains [startDate].
 * Uses the user's [weeklySchedule] (e.g. {"Mon":true,"Tue":false,...}) to determine which
 * days of the week are training days.
 *
 * Example: startDate = 2026-06-03 (Wed), schedule Mon/Wed/Fri → ["2026-06-01","2026-06-03","2026-06-06"]
 */
internal fun computeWorkoutDatesForWeek(
    weeklySchedule: Map<String, Boolean>,
    startDate: LocalDate,
): List<String> {
    if (weeklySchedule.none { it.value }) return emptyList()

    val dayMap = mapOf(
        "Mon" to java.time.DayOfWeek.MONDAY,
        "Tue" to java.time.DayOfWeek.TUESDAY,
        "Wed" to java.time.DayOfWeek.WEDNESDAY,
        "Thu" to java.time.DayOfWeek.THURSDAY,
        "Fri" to java.time.DayOfWeek.FRIDAY,
        "Sat" to java.time.DayOfWeek.SATURDAY,
        "Sun" to java.time.DayOfWeek.SUNDAY,
    )

    // Anchor to the Monday of the week that contains startDate
    val mondayOfWeek = startDate.with(
        java.time.temporal.TemporalAdjusters.previousOrSame(java.time.DayOfWeek.MONDAY)
    )

    return listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
        .filter { weeklySchedule[it] == true }
        .map { dayName ->
            mondayOfWeek.with(
                java.time.temporal.TemporalAdjusters.nextOrSame(dayMap[dayName]!!)
            ).toString()
        }
}

internal fun computeTargetNutrition(profile: AiProfile): NutritionTargets {
    val w = profile.weightKg
    val h = profile.heightCm.toDouble()
    val a = profile.age.toDouble()
    val isMale = profile.sex.equals("MALE", ignoreCase = true)

    // Mifflin-St Jeor BMR
    val bmr = 10.0 * w + 6.25 * h - 5.0 * a + if (isMale) 5.0 else -161.0

    // Activity factor based on training days per week
    val trainingDays = profile.weeklySchedule.count { it.value }
    val activityFactor = when {
        trainingDays == 0 -> 1.2
        trainingDays <= 2 -> 1.375
        trainingDays <= 4 -> 1.55
        else              -> 1.725
    }
    val tdee = bmr * activityFactor

    // Goal adjustment
    val goalUpper = profile.goal.uppercase(Locale.US)
    val targetKcal = when {
        goalUpper.contains("LOSE") || goalUpper.contains("FAT")    -> tdee * 0.85
        goalUpper.contains("GAIN") || goalUpper.contains("MUSCLE") -> tdee * 1.10
        else                                                         -> tdee
    }.coerceAtLeast(bmr).toInt()

    // Macros
    val proteinG = (w * 2.0).toInt().coerceAtLeast((w * 1.6).toInt())    // 2 g/kg
    val fatG     = (w * 0.9).toInt().coerceAtLeast(40)                    // ~0.9 g/kg, min 40g
    val carbsKcal = (targetKcal - proteinG * 4 - fatG * 9).coerceAtLeast(0)
    val carbsG   = carbsKcal / 4

    return NutritionTargets(kcal = targetKcal, proteinG = proteinG, fatG = fatG, carbsG = carbsG)
}

private fun buildPreferencesSummary(profile: AiProfile): String = buildString {
    val weightFormatted = String.format(Locale.US, "%.1f", profile.weightKg)
    appendLine("- Demographics: ${profile.age} y/o ${profile.sex.lowercase(Locale.US)} | ${profile.heightCm} cm | $weightFormatted kg")
    appendLine("- Goal: ${profile.goal}")
    appendLine("- Experience level (1-5): ${profile.experienceLevel}")
    appendLine("- Training mode preference: ${profile.trainingMode}")
    appendLine("- Selected coach visual/persona id: ${profile.coachTrainerId}")

    val equipment = if (profile.equipment.isNotEmpty()) {
        profile.equipment.joinToString(", ")
    } else {
        "bodyweight only"
    }
    appendLine("- Available equipment: $equipment")

    if (profile.injuries.isNotEmpty()) {
        appendLine("- Injuries / limitations: ${profile.injuries.joinToString(", ")}")
    }
    if (profile.healthNotes.isNotEmpty()) {
        appendLine("- Contraindications / medical notes: ${profile.healthNotes.joinToString(", ")}")
    }
    if (profile.weeklySchedule.isNotEmpty()) {
        val available = profile.weeklySchedule.filterValues { it }.keys
        val unavailable = profile.weeklySchedule.filterValues { !it }.keys
        if (available.isNotEmpty()) {
            appendLine("- Preferred training days: ${available.joinToString(", ")}")
        }
        if (unavailable.isNotEmpty()) {
            appendLine("- Rest / unavailable days: ${unavailable.joinToString(", ")}")
        }
    }
    if (profile.dietaryPreferences.isNotEmpty()) {
        // These are PREFERRED foods the athlete WANTS in their plan — not restrictions!
        appendLine("- Preferred foods / dietary style (INCLUDE these where possible): ${profile.dietaryPreferences.joinToString(", ")}")
    }
    if (profile.allergies.isNotEmpty()) {
        appendLine("- Allergies to avoid: ${profile.allergies.joinToString(", ")}")
    }
    appendLine("- Nutrition budget level (1 low .. 3 high): ${profile.budgetLevel ?: 2}")
    if (profile.recentWorkouts.isNotEmpty()) {
        appendLine("- Recent workout outcomes to use for progression/regression decisions:")
        profile.recentWorkouts.take(5).forEachIndexed { index, workout ->
            val completion = (workout.completionRate * 100.0).coerceIn(0.0, 100.0)
            val avgRpe = workout.averageRpe?.let { String.format(Locale.US, "%.1f", it) } ?: "n/a"
            val notes = workout.notes.takeIf { it.isNotBlank() } ?: "none"
            appendLine(
                "  ${index + 1}. ${workout.date}: ${workout.completedItems}/${workout.plannedItems} done, " +
                    "completion ${String.format(Locale.US, "%.0f", completion)}%, " +
                    "volume ${String.format(Locale.US, "%.1f", workout.totalVolumeKg)} kg, avg RPE $avgRpe, notes: $notes"
            )
        }
    }
    if (profile.sleepHistory.isNotEmpty()) {
        appendLine("- Sleep history (last ${profile.sleepHistory.size} nights — use to personalise recovery advice and training intensity):")
        profile.sleepHistory.take(7).forEach { entry ->
            val h = entry.durationMinutes / 60
            val m = entry.durationMinutes % 60
            appendLine("  ${entry.date}: ${h}h ${m}min")
        }
        val avgMinutes = profile.sleepHistory.take(7).map { it.durationMinutes }.average()
        val avgH = avgMinutes.toInt() / 60
        val avgM = avgMinutes.toInt() % 60
        appendLine("  Average sleep: ${avgH}h ${avgM}min/night")
    }
    if (profile.recentWeights.isNotEmpty()) {
        appendLine("- Body weight history (last ${profile.recentWeights.size} measurements — use to fine-tune calorie targets and progression):")
        profile.recentWeights.take(8).forEach { entry ->
            appendLine("  ${entry.date}: ${String.format(Locale.US, "%.1f", entry.weightKg)} kg")
        }
        if (profile.recentWeights.size >= 2) {
            val newest = profile.recentWeights.first().weightKg
            val oldest = profile.recentWeights.last().weightKg
            val diff = newest - oldest
            val trend = when {
                diff > 0.5 -> "gaining weight (+${String.format(Locale.US, "%.1f", diff)} kg)"
                diff < -0.5 -> "losing weight (${String.format(Locale.US, "%.1f", diff)} kg)"
                else -> "stable (${String.format(Locale.US, "%+.1f", diff)} kg)"
            }
            appendLine("  Weight trend: $trend over the recorded period.")
        }
    }
}

