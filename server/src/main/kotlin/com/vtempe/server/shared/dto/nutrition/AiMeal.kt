package com.vtempe.server.shared.dto.nutrition

import kotlinx.serialization.Serializable

@Serializable
data class AiMeal(
    val name: String,
    val ingredients: List<String>,
    val kcal: Int = 0,
    val macros: Macros = Macros(),
)
