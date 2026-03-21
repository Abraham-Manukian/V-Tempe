package com.vtempe.server.features.ai.data.service

import com.vtempe.server.features.ai.domain.port.ExerciseCatalog
import com.vtempe.server.features.ai.domain.port.TrainingPlanResolver
import com.vtempe.server.shared.dto.bootstrap.AiBootstrapRequest
import com.vtempe.server.shared.dto.profile.AiProfile
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
    return buildString {
        appendLine("You are an elite strength coach, nutritionist, and recovery specialist guiding this athlete long-term.")
        appendLine("User language: $languageDisplay. Reply strictly in this language and measurement system.")
        appendLine("Return ONLY a single JSON object and nothing else (no markdown, introductions, or explanations).")
        appendLine("Do not escape quotes (no \\\" sequences). Output must be valid JSON.")
        appendLine()
        appendLine("PROFILE CONTEXT (JSON):")
        appendLine(profileJson)
        appendLine()
        appendLine("KEY FACTS ABOUT THE ATHLETE:")
        append(preferencesSummary)
        appendLine()
        appendLine("NUTRITION RESTRICTIONS (NON-NEGOTIABLE):")
        appendLine(restrictionsSummary)
        appendLine()
        appendLine("RESPONSE SCHEMA (STRICT JSON):")
        appendLine("{\"trainingPlan\": {\"weekIndex\": Int, \"workouts\": [{\"id\": String, \"date\": \"YYYY-MM-DD\", \"sets\": [{\"exerciseId\": String, \"reps\": Int, \"weightKg\": Double?, \"rpe\": Double?}]}]},")
        appendLine(" \"nutritionPlan\": {\"weekIndex\": Int, \"mealsByDay\": {DayLabel: [{\"name\": String, \"ingredients\": [String], \"kcal\": Int, \"macros\": {\"proteinGrams\": Int, \"fatGrams\": Int, \"carbsGrams\": Int, \"kcal\": Int}}]}, \"shoppingList\": [String]},")
        appendLine(" \"sleepAdvice\": {\"messages\": [String], \"disclaimer\": String}}")
        appendLine()
        appendLine("VALID RESPONSE EXAMPLE (structure only, replace with real data):")
        appendLine("{\"trainingPlan\":{\"weekIndex\":0,\"workouts\":[{\"id\":\"w_0_0\",\"date\":\"2025-01-06\",\"sets\":[{\"exerciseId\":\"pattern:knee_dominant\",\"reps\":8,\"weightKg\":60.0,\"rpe\":7.5}]}]},")
        appendLine("\"nutritionPlan\":{\"weekIndex\":0,\"mealsByDay\":{\"Mon\":[{\"name\":\"Oats with berries\",\"ingredients\":[\"rolled oats\",\"milk\",\"berries\"],\"kcal\":420,\"macros\":{\"proteinGrams\":35,\"fatGrams\":12,\"carbsGrams\":55,\"kcal\":420}}]},\"shoppingList\":[\"rolled oats\",\"milk\",\"berries\"]},")
        appendLine("\"sleepAdvice\":{\"messages\":[\"Keep a consistent sleep schedule.\"],\"disclaimer\":\"Not medical advice\"}}")
        appendLine("")
        appendLine("")
        appendLine("{\"messages\":[\"\\u041b\\u043e\\u0436\\u0438\\u0442\\u0435\\u0441\u044c \\u0438 \\u043f\\u0440\u043e\u0441\u044b\u043f\u0430\\u0439\u0442\u0435\u0441\u044c \\u0432 \\u043e\u0434\u043d\u043e \\u0438 \\u0442\u043e \\u0436\\u0435 \\u0432\u0440\u0435\u043c\u044f.\"],\"disclaimer\":\"\\u0421\\u043e\\u0432\u0435\u0442\u044b \\u043d\\u0435 \\u0437\\u0430\u043c\u0435\u043d\u044f\\u044e\u0442 \\u0432\u0440\u0430\u0447\u0430. \\u041f\u0440\u0438 \\u043f\u0440\u043e\u0431\u043b\u0435\u043c\u0430\u0445 \\u043e\u0431\u0440\u0430\u0442\u0438\u0442\u0435\u0441\u044c \\u043a \\u0441\u043f\u0435\u0446\u0438\u0430\u043b\u0438\u0441\u0442\u0443.\"}")
        appendLine("All arrays must close properly. No missing commas, no trailing commas, no duplicated braces.")
        appendLine()
        appendLine("TRAINING RULES:")
        appendLine("- Use ISO dates (YYYY-MM-DD). Plan for week index ${request.weekIndex} (upcoming week).")
        appendLine("- Measurement system: $measurementSystem. Weights must be in $weightUnit (use null for bodyweight).")
        appendLine("- Provide balanced push/pull/legs/core coverage. 4-6 exercises per workout, vary rep ranges 4-12, include RPE 6.5-9.0.")
        appendLine(trainingResolverPrompt)
        appendLine("- Every workout.id must be unique inside trainingPlan. Never repeat workout IDs.")
        appendLine("- Limit workouts to at most 5 in the plan and sets to at most 6 per workout to keep the JSON concise.")
        appendLine()
        appendLine("NUTRITION RULES:")
        appendLine("- Cover every day Mon..Sun. Pick meal frequency dynamically within goal ranges: lose weight -> 3-4 meals/day, maintain -> 3-5 meals/day, gain -> 4-6 meals/day. Do not hardcode two meals; choose what best fits the athlete.")
        appendLine("- Derive daily calories via Mifflin-St Jeor BMR (men: 10*kg + 6.25*cm - 5*age + 5; women: 10*kg + 6.25*cm - 5*age - 161) multiplied by activity factor (use 1.2 if 0 training days, 1.375 for 1-2 days, 1.55 for 3-4 days, 1.725 for 5+ days; if schedule missing assume 3 training days). Adjust for goal: lose = TDEE - 15%, maintain = TDEE, gain = TDEE + 10%. Never drop below calculated BMR.")
        appendLine("- Set daily macros before splitting into meals: protein 1.6-2.2 g/kg bodyweight, fat >= 0.8 g/kg, carbs fill the remaining calories. Distribute totals across meals proportionally and keep integers.")
        appendLine("- The sum of meals for any day must stay within +/-5% of the goal-adjusted daily calories and macros you calculated.")
        appendLine("- Ingredients must be plain text strings (no numbering or markdown). Keep meal names varied, localized, and practical.")
        appendLine("- Never include ingredients that violate allergies/intolerances or restricted foods listed above.")
        appendLine("- Each meal MUST include integer macros {proteinGrams, fatGrams, carbsGrams, kcal}. No field may be null or omitted.")
        appendLine("- Example meal object: {\"name\":\"Power Oats\",\"ingredients\":[\"rolled oats\",\"milk\",\"berries\"],\"kcal\":420,\"macros\":{\"proteinGrams\":35,\"fatGrams\":12,\"carbsGrams\":55,\"kcal\":420}}")
        appendLine("- Ensure macros.kcal equals proteinGrams*4 + carbsGrams*4 + fatGrams*9 (allowed difference +/-20 kcal). If it does not match, adjust kcal to satisfy the formula.")
        appendLine("- Do not include a shoppingList field; it will be computed downstream from the ingredients you provide.")
        appendLine("- Close each day array with ] before defining the next day. No nested day structures.")
        appendLine()
        appendLine("SLEEP ADVICE RULES:")
        appendLine("- Provide 5-7 concise tips referencing training stress and recovery routines.")
        appendLine("- Disclaimer must clearly state the tips are informational and not medical advice.")
        appendLine()
        appendLine("GLOBAL VALIDATION RULES:")
        appendLine("- Training workout dates must be ISO (YYYY-MM-DD) and use future week index ${request.weekIndex}.")
        appendLine("- Day keys in mealsByDay must be exactly Mon,Tue,Wed,Thu,Fri,Sat,Sun in that order.")
        appendLine("- exerciseId values must stay within the resolver slot tokens declared above.")
        appendLine("- For every meal, kcal must follow 4*protein + 4*carbs + 9*fat within +/-20 as already stated.")
        appendLine("- Daily meal frequencies you output must respect the goal ranges listed above; reject plans that fall outside the permitted meal counts.")
        appendLine("- All dates in nutrition or training contexts must be valid calendar dates (ISO format).")
        appendLine("Ensure that every array (for example mealsByDay[Day]) closes with ] before the enclosing object } closes.")
        appendLine("Never output double }} or any sequence like }}} right before ]. Always end JSON with a single }.")
    }
}

private fun buildPreferencesSummary(profile: AiProfile): String = buildString {
    val weightFormatted = String.format(Locale.US, "%.1f", profile.weightKg)
    appendLine("- Demographics: ${profile.age} y/o ${profile.sex.lowercase(Locale.US)} | ${profile.heightCm} cm | $weightFormatted kg")
    appendLine("- Goal: ${profile.goal}")
    appendLine("- Experience level (1-5): ${profile.experienceLevel}")
    appendLine("- Training mode preference: ${profile.trainingMode}")

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
        appendLine("- Dietary preferences: ${profile.dietaryPreferences.joinToString(", ")}")
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
}

