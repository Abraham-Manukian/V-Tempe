package com.vtempe.server.shared.dto.chat

import com.vtempe.server.shared.dto.advice.AiAdviceResponse
import com.vtempe.server.shared.dto.nutrition.AiNutritionResponse
import com.vtempe.server.shared.dto.training.AiTrainingResponse
import kotlinx.serialization.Serializable

@Serializable
data class AiChatResponse(
    val reply: String = "",
    val actions: List<AiChatAction> = emptyList(),
    /** Surgical edits to apply to the user's current plan server-side (see CoachEditOp). Preferred
     *  over rewriting a full trainingPlan/nutritionPlan for small changes. Never forwarded to the
     *  client — CoachEditApplicator turns them into a full plan on the response. */
    val editOps: List<CoachEditOp> = emptyList(),
    val trainingPlan: AiTrainingResponse? = null,
    val nutritionPlan: AiNutritionResponse? = null,
    val sleepAdvice: AiAdviceResponse? = null,
)
