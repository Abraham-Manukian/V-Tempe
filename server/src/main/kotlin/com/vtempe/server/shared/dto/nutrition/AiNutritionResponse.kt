package com.vtempe.server.shared.dto.nutrition

import kotlinx.serialization.Serializable

@Serializable
data class AiNutritionResponse(
    val weekIndex: Int,
    val mealsByDay: Map<String, List<AiMeal>>,
    val shoppingList: List<String> = emptyList(),
)
