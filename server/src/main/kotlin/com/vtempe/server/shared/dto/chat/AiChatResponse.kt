package com.vtempe.server.shared.dto.chat

import com.vtempe.server.shared.dto.advice.AiAdviceResponse
import com.vtempe.server.shared.dto.nutrition.AiNutritionResponse
import com.vtempe.server.shared.dto.training.AiTrainingResponse
import kotlinx.serialization.Serializable

@Serializable
data class AiChatResponse(
    val reply: String,
    val actions: List<AiChatAction> = emptyList(),
    val trainingPlan: AiTrainingResponse? = null,
    val nutritionPlan: AiNutritionResponse? = null,
    val sleepAdvice: AiAdviceResponse? = null,
)
