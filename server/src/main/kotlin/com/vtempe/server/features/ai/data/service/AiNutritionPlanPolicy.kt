package com.vtempe.server.features.ai.data.service

import com.vtempe.server.shared.dto.nutrition.AiMeal
import com.vtempe.server.shared.dto.nutrition.AiNutritionResponse
import com.vtempe.server.shared.dto.nutrition.Macros
import java.util.Locale
import kotlin.collections.linkedMapOf
import kotlin.math.abs

internal const val MacroCalorieTolerance = 40
private const val MinMealsPerDay = 3

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
