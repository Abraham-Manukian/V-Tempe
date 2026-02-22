package com.vtempe.server.shared.dto.nutrition

import kotlinx.serialization.Serializable

@Serializable
data class Macros(
    val proteinGrams: Int = 0,
    val fatGrams: Int = 0,
    val carbsGrams: Int = 0,
    val kcal: Int = 0,
)
