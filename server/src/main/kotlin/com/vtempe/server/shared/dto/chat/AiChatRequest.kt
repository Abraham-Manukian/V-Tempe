package com.vtempe.server.shared.dto.chat

import com.vtempe.server.shared.dto.profile.AiProfile
import kotlinx.serialization.Serializable

import com.vtempe.server.shared.dto.nutrition.AiNutritionResponse
import com.vtempe.server.shared.dto.training.AiTrainingResponse

@Serializable
data class AiChatRequest(
    val profile: AiProfile,
    val messages: List<AiChatMessage>,
    val locale: String? = null,
    /** Full current training plan for the week — lets the coach make precise targeted edits. */
    val currentTrainingPlan: AiTrainingResponse? = null,
    /** Full current nutrition plan — lets the coach modify individual meals by day. */
    val currentNutritionPlan: AiNutritionResponse? = null,
)
