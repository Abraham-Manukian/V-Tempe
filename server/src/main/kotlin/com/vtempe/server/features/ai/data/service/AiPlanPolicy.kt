package com.vtempe.server.features.ai.data.service

import com.vtempe.server.shared.dto.advice.AiAdviceResponse
import com.vtempe.server.shared.dto.bootstrap.AiBootstrapResponse
import com.vtempe.server.shared.dto.nutrition.AiMeal
import com.vtempe.server.shared.dto.nutrition.AiNutritionResponse
import com.vtempe.server.shared.dto.nutrition.Macros
import com.vtempe.server.shared.dto.training.AiSet
import com.vtempe.server.shared.dto.training.AiTrainingResponse
import com.vtempe.server.shared.dto.training.AiWorkout
import java.nio.ByteBuffer
import java.nio.charset.Charset
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.temporal.TemporalAdjusters
import java.util.Locale
import kotlin.collections.linkedMapOf
import kotlin.math.abs

internal fun measurementSystemLabel(locale: Locale): String =
    if (usesImperial(locale)) "imperial (pounds/inches)" else "metric (kilograms/centimeters)"

internal const val MacroCalorieTolerance = 40
private const val MaxWorkoutsPerPlan = 5
private const val MaxSetsPerWorkout = 6
private const val MinMealsPerDay = 3

private val allowedExerciseIds = setOf(
    "squat",
    "bench",
    "deadlift",
    "ohp",
    "row",
    "pullup",
    "lunge",
    "dip",
    "pushup",
    "curl",
    "tricep_extension",
    "plank",
    "hip_thrust",
    "leg_press",
    "run",
    "bike",
    "yoga"
)

private val exerciseAliasMap = mapOf(
    "back_squat" to "squat",
    "bench_press" to "bench",
    "bent_over_row" to "row",
    "barbell_row" to "row",
    "pull_up" to "pullup",
    "pullups" to "pullup",
    "walking_lunge" to "lunge",
    "parallel_bar_dip" to "dip",
    "parallel_bar_dips" to "dip",
    "push_up" to "pushup",
    "push_ups" to "pushup",
    "bicep_curl" to "curl",
    "biceps_curl" to "curl",
    "biceps_curls" to "curl",
    "triceps_extension" to "tricep_extension",
    "triceps_extensions" to "tricep_extension",
    "hipthrust" to "hip_thrust",
    "hip_thrusts" to "hip_thrust",
    "plank_hold" to "plank",
    "legpress" to "leg_press",
    "cycling" to "bike"
)

private val cp1251Charset: Charset = Charset.forName("windows-1251")
private val cyrillicRange = '\u0400'..'\u04FF'
private val whitespaceRegex = Regex("\\s+")

internal fun sanitizeText(raw: String): String {
    val trimmed = raw.trim()
    val decoded = decodeCp1251(trimmed) ?: trimmed
    return whitespaceRegex.replace(decoded, " ").trim()
}
private fun decodeCp1251(raw: String): String? {
    if (!looksLikeCp1251Garbage(raw)) return null
    val bytes = raw.map { (it.code and 0xFF).toByte() }.toByteArray()
    return runCatching {
        val decoded = cp1251Charset.decode(ByteBuffer.wrap(bytes)).toString()
        val cyrillicCount = decoded.count { it in cyrillicRange }
        if (cyrillicCount >= decoded.length / 3) decoded else null
    }.getOrNull()
}

private fun looksLikeCp1251Garbage(raw: String): Boolean =
    raw.any { ch ->
        ch.code in 0x2500..0x257F ||
            ch.code in 0x2580..0x259F ||
            ch.code in 0x00C0..0x00FF
    }

internal fun normalizeTrainingPlan(plan: AiTrainingResponse): AiTrainingResponse {
    val weekStart = expectedWeekStart(plan.weekIndex)
    val usedWorkoutIds = mutableSetOf<String>()
    val workouts = plan.workouts
        .take(MaxWorkoutsPerPlan)
        .mapIndexed { index, workout ->
            val safeDate = normalizeWorkoutDate(workout.date, weekStart, index)
            val rawId = sanitizeText(workout.id).ifEmpty { "w_${plan.weekIndex}_$index" }
            val safeId = uniqueWorkoutId(rawId, plan.weekIndex, index, usedWorkoutIds)

            val normalizedSets = workout.sets
                .mapNotNull { set ->
                    val canonical = canonicalExerciseIdOrNull(set.exerciseId) ?: return@mapNotNull null
                    val reps = set.reps.coerceAtLeast(1)
                    val weight = set.weightKg?.takeIf { it >= 0.0 }
                    val rpe = set.rpe?.takeIf { it > 0.0 }
                    AiSet(
                        exerciseId = canonical,
                        reps = reps,
                        weightKg = weight,
                        rpe = rpe
                    )
                }
                .distinctBy { listOf(it.exerciseId, it.reps, it.weightKg, it.rpe) }
                .take(MaxSetsPerWorkout)

            val safeSets = if (normalizedSets.isEmpty()) {
                listOf(AiSet("squat", reps = 8, weightKg = null, rpe = 7.0))
            } else {
                normalizedSets
            }

            AiWorkout(
                id = safeId,
                date = safeDate,
                sets = safeSets
            )
        }

    return plan.copy(workouts = workouts)
}

internal fun normalizeNutritionPlan(plan: AiNutritionResponse, locale: Locale): AiNutritionResponse {
    val fallbackMeals = templateMeals(locale)
    val normalizedMealsByDay = linkedMapOf<String, List<AiMeal>>()

    plan.mealsByDay.forEach { (dayRaw, meals) ->
        val dayKey = sanitizeText(dayRaw).ifEmpty { dayRaw.trim() }.ifEmpty { "Day" }
        val cleanedMeals = meals.mapNotNull { meal ->
            val name = sanitizeText(meal.name)
            val ingredients = meal.ingredients.map(::sanitizeText).filter { it.isNotEmpty() }
            if (name.isEmpty() || ingredients.isEmpty()) {
                null
            } else {
                val normalizedMacros = normalizeMacros(meal.macros)
                val normalizedKcal = if (meal.kcal <= 0 || abs(meal.kcal - normalizedMacros.kcal) > MacroCalorieTolerance) {
                    normalizedMacros.kcal
                } else {
                    meal.kcal
                }
                AiMeal(
                    name = name,
                    ingredients = ingredients,
                    kcal = normalizedKcal,
                    macros = normalizedMacros
                )
            }
        }
        val ruLocale = locale.language.equals("ru", ignoreCase = true)
        val cyrillicDetected = cleanedMeals.any { meal ->
            hasCyrillic(meal.name) || meal.ingredients.any(::hasCyrillic)
        }
        val baseMeals = if (cleanedMeals.isEmpty() || (ruLocale && !cyrillicDetected)) fallbackMeals else cleanedMeals
        val safeMeals = ensureMinimumMealsPerDay(baseMeals, fallbackMeals, MinMealsPerDay)
        normalizedMealsByDay[dayKey] = safeMeals
    }

    val normalizedShopping = (plan.shoppingList + normalizedMealsByDay.values.flatten().flatMap { it.ingredients })
        .map(::sanitizeText)
        .filter { it.isNotEmpty() }
        .distinct()
        .sorted()

    return plan.copy(
        mealsByDay = normalizedMealsByDay,
        shoppingList = normalizedShopping
    )
}

internal fun normalizeAdvice(advice: AiAdviceResponse): AiAdviceResponse {
    val normalizedMessages = advice.messages.map(::sanitizeText).filter { it.isNotEmpty() }
    val normalizedDisclaimer = advice.disclaimer?.let(::sanitizeText)?.takeIf { it.isNotEmpty() }
    return advice.copy(
        messages = if (normalizedMessages.isEmpty()) advice.messages else normalizedMessages,
        disclaimer = normalizedDisclaimer ?: advice.disclaimer
    )
}

internal fun normalizeBundle(bundle: AiBootstrapResponse, locale: Locale): AiBootstrapResponse =
    AiBootstrapResponse(
        trainingPlan = bundle.trainingPlan?.let { normalizeTrainingPlan(it) },
        nutritionPlan = bundle.nutritionPlan?.let { normalizeNutritionPlan(it, locale) },
        sleepAdvice = bundle.sleepAdvice?.let { normalizeAdvice(it) }
    )

internal fun normalizeBundle(bundle: AiBootstrapResponse): AiBootstrapResponse =
    normalizeBundle(bundle, Locale.ENGLISH)

internal fun templateMeals(locale: Locale): List<AiMeal> {
    val isRu = locale.language.equals("ru", ignoreCase = true)
    return if (isRu) {
        listOf(
            AiMeal(
                name = "\u041e\u0432\u0441\u044f\u043d\u043a\u0430 \u0441 \u044f\u0433\u043e\u0434\u0430\u043c\u0438",
                ingredients = listOf(
                    "\u043e\u0432\u0441\u044f\u043d\u044b\u0435 \u0445\u043b\u043e\u043f\u044c\u044f",
                    "\u043c\u043e\u043b\u043e\u043a\u043e",
                    "\u044f\u0433\u043e\u0434\u044b",
                    "\u043c\u0451\u0434"
                ),
                kcal = 420,
                macros = Macros(35, 12, 55, 420)
            ),
            AiMeal(
                name = "\u041a\u0443\u0440\u0438\u043d\u0430\u044f \u0433\u0440\u0443\u0434\u043a\u0430 \u0441 \u0440\u0438\u0441\u043e\u043c \u0438 \u043e\u0432\u043e\u0449\u0430\u043c\u0438",
                ingredients = listOf(
                    "\u043a\u0443\u0440\u0438\u043d\u0430\u044f \u0433\u0440\u0443\u0434\u043a\u0430",
                    "\u0440\u0438\u0441",
                    "\u0431\u0440\u043e\u043a\u043a\u043e\u043b\u0438",
                    "\u043c\u043e\u0440\u043a\u043e\u0432\u044c"
                ),
                kcal = 520,
                macros = Macros(45, 15, 50, 520)
            ),
            AiMeal(
                name = "\u0413\u0440\u0435\u0447\u0435\u0441\u043a\u0438\u0439 \u0439\u043e\u0433\u0443\u0440\u0442 \u0441 \u043e\u0440\u0435\u0445\u0430\u043c\u0438",
                ingredients = listOf(
                    "\u0433\u0440\u0435\u0447\u0435\u0441\u043a\u0438\u0439 \u0439\u043e\u0433\u0443\u0440\u0442",
                    "\u0433\u0440\u0435\u0446\u043a\u0438\u0435 \u043e\u0440\u0435\u0445\u0438",
                    "\u043c\u0451\u0434"
                ),
                kcal = 420,
                macros = Macros(25, 18, 35, 420)
            ),
            AiMeal(
                name = "\u041b\u043e\u0441\u043e\u0441\u044c \u0441 \u043a\u0438\u043d\u043e\u0430 \u0438 \u0448\u043f\u0438\u043d\u0430\u0442\u043e\u043c",
                ingredients = listOf(
                    "\u0444\u0438\u043b\u0435 \u043b\u043e\u0441\u043e\u0441\u044f",
                    "\u043a\u0438\u043d\u043e\u0430",
                    "\u0448\u043f\u0438\u043d\u0430\u0442",
                    "\u043b\u0438\u043c\u043e\u043d"
                ),
                kcal = 560,
                macros = Macros(40, 20, 45, 560)
            )
        )
    } else {
        listOf(
            AiMeal(
                name = "Oatmeal with Berries",
                ingredients = listOf("rolled oats", "milk", "blueberries", "honey"),
                kcal = 420,
                macros = Macros(35, 12, 55, 420)
            ),
            AiMeal(
                name = "Chicken Bowl with Veggies",
                ingredients = listOf("chicken breast", "rice", "broccoli", "carrots"),
                kcal = 520,
                macros = Macros(45, 15, 50, 520)
            ),
            AiMeal(
                name = "Greek Yogurt Parfait",
                ingredients = listOf("greek yogurt", "walnuts", "honey"),
                kcal = 420,
                macros = Macros(25, 18, 35, 420)
            ),
            AiMeal(
                name = "Salmon with Quinoa and Spinach",
                ingredients = listOf("salmon fillet", "quinoa", "spinach", "lemon"),
                kcal = 560,
                macros = Macros(40, 20, 45, 560)
            )
        )
    }
}

private fun expectedWeekStart(weekIndex: Int): LocalDate {
    val today = LocalDate.now(ZoneOffset.UTC)
    val nextMonday = today.with(TemporalAdjusters.nextOrSame(DayOfWeek.MONDAY))
    return nextMonday.plusWeeks(weekIndex.toLong())
}

private fun normalizeWorkoutDate(raw: String, weekStart: LocalDate, index: Int): String {
    val parsed = runCatching { LocalDate.parse(raw) }.getOrElse { weekStart.plusDays(index.toLong()) }
    val weekEnd = weekStart.plusDays(6)
    val safeDate = if (parsed.isBefore(weekStart) || parsed.isAfter(weekEnd)) {
        weekStart.plusDays(index.toLong().coerceAtMost(6))
    } else {
        parsed
    }
    return safeDate.toString()
}

private fun uniqueWorkoutId(
    rawId: String,
    weekIndex: Int,
    index: Int,
    used: MutableSet<String>
): String {
    val normalized = normalizeExerciseToken(rawId).ifEmpty { "w_${weekIndex}_$index" }
    var candidate = normalized
    var suffix = 1
    while (!used.add(candidate)) {
        candidate = "${normalized}_$suffix"
        suffix += 1
    }
    return candidate
}

private fun canonicalExerciseIdOrNull(raw: String): String? {
    val trimmed = sanitizeText(raw)
    if (trimmed.isEmpty()) return null

    val explicitId = Regex("""\(([^()]+)\)\s*$""")
        .find(trimmed)
        ?.groupValues
        ?.getOrNull(1)
        ?.let(::normalizeExerciseToken)
        ?.let { token -> if (token in allowedExerciseIds) token else exerciseAliasMap[token] }
    if (explicitId != null) return explicitId

    val token = normalizeExerciseToken(trimmed)
    return when {
        token in allowedExerciseIds -> token
        else -> exerciseAliasMap[token]
    }
}

private fun normalizeExerciseToken(raw: String): String =
    sanitizeText(raw)
        .lowercase(Locale.US)
        .replace('-', '_')
        .replace(' ', '_')
        .replace(Regex("_+"), "_")
        .trim('_')

private fun hasCyrillic(text: String): Boolean = text.any { it in cyrillicRange }

private fun ensureMinimumMealsPerDay(
    meals: List<AiMeal>,
    fallbackMeals: List<AiMeal>,
    minCount: Int
): List<AiMeal> {
    if (meals.size >= minCount) return meals

    val normalizedExisting = meals.map { meal ->
        normalizeExerciseToken(meal.name)
    }.toMutableSet()

    val padded = meals.toMutableList()
    fallbackMeals.forEach { fallback ->
        if (padded.size >= minCount) return@forEach
        val key = normalizeExerciseToken(fallback.name)
        if (key !in normalizedExisting) {
            padded += fallback
            normalizedExisting += key
        }
    }

    if (padded.size < minCount) {
        var index = 0
        while (padded.size < minCount && fallbackMeals.isNotEmpty()) {
            padded += fallbackMeals[index % fallbackMeals.size]
            index += 1
        }
    }

    return padded
}

internal fun validateTrainingPlan(plan: AiTrainingResponse): String? {
    if (plan.workouts.isEmpty()) return "workouts array must contain at least one workout"
    if (plan.workouts.any { it.sets.isEmpty() }) return "each workout must include at least one set"

    val workoutIds = mutableSetOf<String>()
    plan.workouts.forEachIndexed { workoutIndex, workout ->
        val id = normalizeExerciseToken(workout.id)
        if (id.isBlank()) return "workout[$workoutIndex].id must not be blank"
        if (!workoutIds.add(id)) return "workout ids must be unique"

        if (workout.sets.size > MaxSetsPerWorkout * 2) {
            return "workout[$workoutIndex] has too many sets (${workout.sets.size})"
        }
        val seenSets = mutableSetOf<String>()
        workout.sets.forEachIndexed { setIndex, set ->
            val canonicalExercise = canonicalExerciseIdOrNull(set.exerciseId)
                ?: return "workout[$workoutIndex].sets[$setIndex].exerciseId is not supported"
            if (set.reps <= 0) return "workout[$workoutIndex].sets[$setIndex].reps must be positive"
            val fingerprint = "$canonicalExercise|${set.reps}|${set.weightKg ?: "bw"}|${set.rpe ?: "-"}"
            if (!seenSets.add(fingerprint)) {
                return "workout[$workoutIndex] contains duplicate sets"
            }
        }
    }
    return null
}

internal fun validateNutritionPlan(plan: AiNutritionResponse): String? {
    if (plan.mealsByDay.isEmpty()) return "mealsByDay must contain entries for the week"
    val requiredDays = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
    val missingDays = requiredDays.filter { day -> day !in plan.mealsByDay.keys }
    if (missingDays.isNotEmpty()) return "mealsByDay missing required days: ${missingDays.joinToString(",")}"

    requiredDays.forEach { day ->
        val meals = plan.mealsByDay[day].orEmpty()
        if (meals.size < MinMealsPerDay) {
            return "$day must include at least $MinMealsPerDay meals"
        }
        val validMeals = meals.count { meal ->
            meal.name.isNotBlank() && meal.ingredients.any { it.isNotBlank() }
        }
        if (validMeals == 0) {
            return "$day must include at least one valid meal with name and ingredients"
        }
    }
    return null
}

internal fun validateSleepAdvice(advice: AiAdviceResponse): String? {
    if (advice.messages.isEmpty()) return "messages must contain at least one tip"
    return null
}

internal fun normalizeMacros(macros: Macros): Macros {
    val protein = macros.proteinGrams.coerceAtLeast(0)
    val fat = macros.fatGrams.coerceAtLeast(0)
    val carbs = macros.carbsGrams.coerceAtLeast(0)
    val computedKcal = computeKcal(protein, carbs, fat)
    val kcal = when {
        macros.kcal <= 0 -> computedKcal
        abs(macros.kcal - computedKcal) > MacroCalorieTolerance -> computedKcal
        else -> macros.kcal
    }
    return Macros(proteinGrams = protein, fatGrams = fat, carbsGrams = carbs, kcal = kcal)
}

internal fun computeKcal(proteinGrams: Int, carbsGrams: Int, fatGrams: Int): Int =
    proteinGrams.coerceAtLeast(0) * 4 +
        carbsGrams.coerceAtLeast(0) * 4 +
        fatGrams.coerceAtLeast(0) * 9

internal fun safeLocale(tag: String?): Locale {
    val candidate = tag?.let { runCatching { Locale.forLanguageTag(it) }.getOrNull() }
    return if (candidate == null || candidate.language.isNullOrBlank()) Locale.ENGLISH else candidate
}

private fun usesImperial(locale: Locale): Boolean =
    locale.country.equals("US", ignoreCase = true) ||
        locale.country.equals("LR", ignoreCase = true) ||
        locale.country.equals("MM", ignoreCase = true)
