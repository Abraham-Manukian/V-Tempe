package com.vtempe.server.shared.dto.nutrition

import com.vtempe.server.shared.dto.profile.AiProfile
import kotlinx.serialization.Serializable

@Serializable
data class Macros(
    val proteinGrams: Int = 0,
    val fatGrams: Int = 0,
    val carbsGrams: Int = 0,
    val kcal: Int = 0,
)

@Serializable
data class AiMeal(
    val name: String,
    val ingredients: List<String>,
    val kcal: Int = 0,
    val macros: Macros = Macros(),
)

@Serializable
data class AiNutritionRequest(
    val profile: AiProfile,
    val weekIndex: Int,
    val locale: String? = null,
)

@Serializable
data class AiNutritionResponse(
    val weekIndex: Int,
    val mealsByDay: Map<String, List<AiMeal>>,
    val shoppingList: List<String> = emptyList(),
)
