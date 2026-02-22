package com.vtempe.server.shared.dto.nutrition

import com.vtempe.server.shared.dto.profile.AiProfile
import kotlinx.serialization.Serializable

@Serializable
data class AiNutritionRequest(
    val profile: AiProfile,
    val weekIndex: Int,
    val locale: String? = null,
)
