package com.vtempe.server.shared.dto.nutrition

import kotlinx.serialization.Serializable

@Serializable
data class AiMeal(
    val name: String,
    val ingredients: List<String>,
    val kcal: Int = 0,
    val macros: Macros = Macros(),
    /**
     * Allergen tags explicitly assigned by the LLM.
     * Values match [FoodRestrictionTag] names: "Dairy", "TreeNut", "Gluten", etc.
     * Empty list means no allergens detected by the model.
     * Default-empty for backward compatibility with cached/old responses.
     */
    val allergenTags: List<String> = emptyList(),
)
