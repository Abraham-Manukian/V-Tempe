package com.vtempe.server.features.ai.data.service

import com.vtempe.server.shared.dto.advice.AiAdviceRequest
import com.vtempe.server.shared.dto.advice.AiAdviceResponse
import com.vtempe.server.shared.dto.nutrition.AiNutritionRequest
import com.vtempe.server.shared.dto.nutrition.AiNutritionResponse
import com.vtempe.server.shared.dto.training.AiSet
import com.vtempe.server.shared.dto.training.AiTrainingRequest
import com.vtempe.server.shared.dto.training.AiTrainingResponse
import com.vtempe.server.shared.dto.training.AiWorkout
import java.time.LocalDate
import java.time.ZoneOffset

internal fun fallbackTraining(req: AiTrainingRequest): AiTrainingResponse {
    val today = LocalDate.now(ZoneOffset.UTC)
    fun set(id: String, reps: Int, weight: Double?, rpe: Double?) =
        AiSet(id, reps, weight, rpe)

    val workouts = List(3) { day ->
        val id = "w_${req.weekIndex}_${day + 1}"
        val date = today.plusDays(day.toLong()).toString()
        val sets = when (day) {
            0 -> listOf(
                set("pattern:knee_dominant", 6, 60.0, 7.0),
                set("pattern:horizontal_push", 8, 45.0, 7.0),
                set("pattern:horizontal_pull", 10, 40.0, 7.0)
            )
            1 -> listOf(
                set("pattern:hinge", 4, 90.0, 7.5),
                set("pattern:vertical_push", 8, 30.0, 7.0),
                set("pattern:vertical_pull", 6, null, 7.0)
            )
            else -> listOf(
                set("pattern:single_leg", 10, 25.0, 7.0),
                set("pattern:horizontal_push", 12, 20.0, 7.0),
                set("pattern:core", 45, null, 6.5)
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
    val days = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
    val planMeals = days.associateWith { day -> if (day == "Sun") meals.take(3) else meals }
    val shopping = meals.flatMap { it.ingredients }
        .map(::sanitizeText)
        .filter { it.isNotEmpty() }
        .distinct()
        .sorted()
    return AiNutritionResponse(req.weekIndex, planMeals, shopping)
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
