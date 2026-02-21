package com.vtempe.server.shared.dto.advice

import com.vtempe.server.shared.dto.profile.AiProfile
import kotlinx.serialization.Serializable

@Serializable
data class AiAdviceRequest(
    val profile: AiProfile,
    val locale: String? = null,
)

@Serializable
data class AiAdviceResponse(
    val messages: List<String>,
    val disclaimer: String? = "Not medical advice",
)
