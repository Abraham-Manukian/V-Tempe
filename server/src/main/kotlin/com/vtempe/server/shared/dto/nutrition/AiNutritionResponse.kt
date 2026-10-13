package com.vtempe.server.shared.dto.nutrition

import kotlinx.serialization.Serializable

@Serializable
data class AiNutritionResponse(
    val weekIndex: Int,
    val mealsByDay: Map<String, List<AiMeal>>,
    val shoppingList: List<String> = emptyList(),
    /** Set only for an approximate menu (the fallback when the model is unavailable): its meals
     *  name the food but state no portions or numbers, and this is the day's target instead
     *  (VT-AUD-036). Null for a regular plan, whose meals carry their own numbers. */
    val dailyTarget: Macros? = null,
)
