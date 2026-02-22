package com.vtempe.server.shared.dto.bootstrap

import com.vtempe.server.shared.dto.advice.AiAdviceResponse
import com.vtempe.server.shared.dto.nutrition.AiNutritionResponse
import com.vtempe.server.shared.dto.training.AiTrainingResponse
import kotlinx.serialization.Serializable

@Serializable
data class AiBootstrapResponse(
    val trainingPlan: AiTrainingResponse? = null,
    val nutritionPlan: AiNutritionResponse? = null,
    val sleepAdvice: AiAdviceResponse? = null,
)
