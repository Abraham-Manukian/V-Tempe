package com.vtempe.server.features.ai.data.service

import com.vtempe.server.features.ai.data.service.split.TrainingSplitPlanner
import com.vtempe.server.shared.dto.advice.AiAdviceRequest
import com.vtempe.server.shared.dto.advice.AiAdviceResponse
import com.vtempe.server.shared.dto.nutrition.AiMeal
import com.vtempe.server.shared.dto.nutrition.AiNutritionRequest
import com.vtempe.server.shared.dto.nutrition.AiNutritionResponse
import com.vtempe.server.shared.dto.nutrition.Macros
import com.vtempe.server.shared.dto.training.AiSet
import com.vtempe.server.shared.dto.training.AiTrainingRequest
import com.vtempe.server.shared.dto.training.AiTrainingResponse
import com.vtempe.server.shared.dto.training.AiWorkout
import java.time.LocalDate
import java.time.ZoneOffset
import kotlin.math.roundToInt

internal fun fallbackTraining(req: AiTrainingRequest): AiTrainingResponse {
    val today = LocalDate.now(ZoneOffset.UTC)

    // The AiSet values below are placeholders — normalizeTrainingPlan always overrides
    // exerciseId/sets/rpe from the skeleton it independently recomputes when a profile is
    // present (see computeSkeletonData in AiTrainingPlanPolicy.kt). What actually matters here
    // is building that skeleton through the SAME TrainingSplitPlanner the LLM path uses (A4) —
    // a separate hardcoded template drifted from real split/slot counts (e.g. a 5-day PPL
    // split has more slots per day than a fixed 3-slot template) and seeded weightKg with
    // numbers unrelated to the user's experience/equipment. The fallback should differ from
    // the normal path only in not calling an LLM at all.
    val trainingDays = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
        .filter { req.profile.weeklySchedule[it] == true }
        .ifEmpty { listOf("Mon", "Wed", "Fri") }
    val skeletons = TrainingSplitPlanner.build(
        trainingDays        = trainingDays,
        focusRaw            = req.profile.trainingFocus,
        goalRaw             = req.profile.goal,
        splitPreferenceRaw  = req.profile.splitPreference,
        experienceLevel     = req.profile.experienceLevel,
        age                 = req.profile.age,
        sexRaw              = req.profile.sex,
        lifestyleRaw        = req.profile.lifestyleActivity,
        injuries            = req.profile.injuries,
        sessionDurationMins = req.profile.sessionDurationMins,
        weekIndex           = req.weekIndex,
        forceDeload         = shouldForceDeload(req.profile.recentWorkouts),
        hasHistory          = req.profile.recentWorkouts.isNotEmpty()
    )
    val workouts = skeletons.mapIndexed { day, skeleton ->
        val id = "w_${req.weekIndex}_${day + 1}"
        val date = today.plusDays(day.toLong()).toString()
        val sets = skeleton.slots.map { slot ->
            // No LLM to ask for a realistic weight — leave it null (UI shows "—") rather than
            // inventing a number unrelated to this user's experience/equipment.
            AiSet(
                exerciseId = slot.pattern.token,
                reps = (slot.repMin + slot.repMax) / 2,
                weightKg = null,
                rpe = slot.rpeTarget.toDouble()
            )
        }
        AiWorkout(id = id, date = date, sets = sets)
    }
    return normalizeTrainingPlan(
        plan = AiTrainingResponse(req.weekIndex, workouts),
        profile = req.profile,
        trainingPlanResolver = builtInTrainingPlanResolver
    )
}

internal fun fallbackNutrition(req: AiNutritionRequest): AiNutritionResponse {
    val locale = safeLocale(req.locale ?: req.profile.locale)
    val meals = templateMeals(locale, req.profile)
    val targetKcal = computeTargetNutrition(req.profile).kcal
    val fullDayMeals = scaleMealsToTarget(meals, targetKcal)
    val shortDayMeals = scaleMealsToTarget(meals.take(3), targetKcal)
    val days = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
    val planMeals = days.associateWith { day -> if (day == "Sun") shortDayMeals else fullDayMeals }
    val shopping = meals.flatMap { it.ingredients }
        .map(::sanitizeText)
        .filter { it.isNotEmpty() }
        .distinct()
        .sorted()
    return AiNutritionResponse(req.weekIndex, planMeals, shopping)
}

/** Scales a fixed meal template's kcal/macros to hit [targetKcal] — ingredient names stay
 *  generic (no gram quantities to rewrite), only the numbers are proportionally adjusted. */
private fun scaleMealsToTarget(meals: List<AiMeal>, targetKcal: Int): List<AiMeal> {
    val currentTotal = meals.sumOf { it.kcal }
    if (currentTotal <= 0 || targetKcal <= 0) return meals
    val factor = targetKcal.toDouble() / currentTotal
    return meals.map { meal ->
        val scaledKcal = (meal.kcal * factor).roundToInt()
        AiMeal(
            name = meal.name,
            ingredients = meal.ingredients,
            kcal = scaledKcal,
            macros = Macros(
                proteinGrams = (meal.macros.proteinGrams * factor).roundToInt(),
                fatGrams = (meal.macros.fatGrams * factor).roundToInt(),
                carbsGrams = (meal.macros.carbsGrams * factor).roundToInt(),
                kcal = scaledKcal
            ),
            allergenTags = meal.allergenTags,
            recipe = meal.recipe
        )
    }
}

internal fun fallbackAdvice(req: AiAdviceRequest): AiAdviceResponse {
    val locale = safeLocale(req.locale ?: req.profile.locale)
    val isRu = locale.language.equals("ru", ignoreCase = true)
    val messages = if (isRu) {
        listOf(
            "\u041b\u043e\u0436\u0438\u0442\u0435\u0441\u044c \u0438 \u043f\u0440\u043e\u0441\u044b\u043f\u0430\u0439\u0442\u0435\u0441\u044c \u0432 \u043e\u0434\u043d\u043e \u0438 \u0442\u043e \u0436\u0435 \u0432\u0440\u0435\u043c\u044f, \u0434\u0430\u0436\u0435 \u043f\u043e \u0432\u044b\u0445\u043e\u0434\u043d\u044b\u043c.",
            "\u0417\u0430 \u0447\u0430\u0441 \u0434\u043e \u0441\u043d\u0430 \u043f\u0440\u0438\u0433\u043b\u0443\u0448\u0438\u0442\u0435 \u0441\u0432\u0435\u0442 \u0438 \u0443\u0431\u0435\u0440\u0438\u0442\u0435 \u044f\u0440\u043a\u0438\u0435 \u044d\u043a\u0440\u0430\u043d\u044b.",
            "\u041f\u043e\u0434\u0434\u0435\u0440\u0436\u0438\u0432\u0430\u0439\u0442\u0435 \u043f\u0440\u043e\u0445\u043b\u0430\u0434\u0443 \u0438 \u0442\u0438\u0448\u0438\u043d\u0443 \u0432 \u0441\u043f\u0430\u043b\u044c\u043d\u0435 (18\u201320 \u00b0C).",
            "\u0418\u0437\u0431\u0435\u0433\u0430\u0439\u0442\u0435 \u0442\u044f\u0436\u0451\u043b\u043e\u0439 \u0435\u0434\u044b \u0438 \u043a\u043e\u0444\u0435\u0438\u043d\u0430 \u0437\u0430 3 \u0447\u0430\u0441\u0430 \u0434\u043e \u0441\u043d\u0430.",
            "\u0414\u043e\u0431\u0430\u0432\u044c\u0442\u0435 \u043b\u0451\u0433\u043a\u0443\u044e \u0440\u0430\u0441\u0442\u044f\u0436\u043a\u0443 \u0438\u043b\u0438 \u0434\u044b\u0445\u0430\u0442\u0435\u043b\u044c\u043d\u044b\u0435 \u0443\u043f\u0440\u0430\u0436\u043d\u0435\u043d\u0438\u044f \u043f\u0435\u0440\u0435\u0434 \u0441\u043d\u043e\u043c.",
            "\u0415\u0441\u043b\u0438 \u043d\u043e\u0447\u044c \u043f\u0440\u043e\u0448\u043b\u0430 \u043f\u043b\u043e\u0445\u043e, \u0441\u043d\u0438\u0437\u044c\u0442\u0435 \u043d\u0430\u0433\u0440\u0443\u0437\u043a\u0443 \u043d\u0430 \u0441\u043b\u0435\u0434\u0443\u044e\u0449\u0435\u0439 \u0442\u0440\u0435\u043d\u0438\u0440\u043e\u0432\u043a\u0435."
        )
    } else {
        listOf(
            "Stick to consistent bed and wake times, including weekends.",
            "Dim screens and bright lights at least an hour before bed.",
            "Keep the bedroom cool, dark, and ventilated (18-20C).",
            "Avoid heavy meals and caffeine within three hours of bedtime.",
            "Schedule light stretching or breathing exercises to wind down.",
            "Reduce training load the day after poor sleep."
        )
    }
    val disclaimer = if (isRu) {
        "\u0421\u043e\u0432\u0435\u0442\u044b \u043d\u043e\u0441\u044f\u0442 \u043e\u0437\u043d\u0430\u043a\u043e\u043c\u0438\u0442\u0435\u043b\u044c\u043d\u044b\u0439 \u0445\u0430\u0440\u0430\u043a\u0442\u0435\u0440. \u041f\u0440\u0438 \u0441\u0435\u0440\u044c\u0451\u0437\u043d\u044b\u0445 \u043d\u0430\u0440\u0443\u0448\u0435\u043d\u0438\u044f\u0445 \u0441\u043d\u0430 \u043e\u0431\u0440\u0430\u0442\u0438\u0442\u0435\u0441\u044c \u043a \u0432\u0440\u0430\u0447\u0443."
    } else {
        "Coaching tips only. Consult a medical professional if sleep issues persist."
    }
    return AiAdviceResponse(messages, disclaimer)
}
