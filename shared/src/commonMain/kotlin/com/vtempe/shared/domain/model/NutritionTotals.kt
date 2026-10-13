package com.vtempe.shared.domain.model

/** False for a dish of an approximate menu: it names the food but states no numbers (VT-AUD-036). */
val Meal.hasNutrition: Boolean
    get() = kcal > 0 || macros.proteinGrams > 0 || macros.fatGrams > 0 || macros.carbsGrams > 0

/** An approximate menu: one daily target instead of per-dish numbers (VT-AUD-036). */
val NutritionPlan.isApproximate: Boolean get() = dailyTarget != null

/**
 * What the user aims for on [day]: an approximate menu's daily target, otherwise the sum of the
 * day's meals. Null when the plan has no meals that day. Every screen totals through here, so an
 * approximate menu never shows a day of "0 kcal".
 */
fun NutritionPlan.dayTotals(day: String): Macros? {
    val meals = mealsByDay[day].orEmpty()
    if (meals.isEmpty()) return null
    return dailyTarget ?: Macros(
        proteinGrams = meals.sumOf { it.macros.proteinGrams },
        fatGrams = meals.sumOf { it.macros.fatGrams },
        carbsGrams = meals.sumOf { it.macros.carbsGrams },
        kcal = meals.sumOf { it.kcal },
    )
}

/** [dayTotals] summed over every day of the plan. */
fun NutritionPlan.weekTotals(): Macros =
    mealsByDay.keys.mapNotNull(::dayTotals).fold(Macros(0, 0, 0, 0)) { sum, day ->
        Macros(
            proteinGrams = sum.proteinGrams + day.proteinGrams,
            fatGrams = sum.fatGrams + day.fatGrams,
            carbsGrams = sum.carbsGrams + day.carbsGrams,
            kcal = sum.kcal + day.kcal,
        )
    }
