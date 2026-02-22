package com.vtempe.server.shared.dto.advice

import kotlinx.serialization.Serializable

@Serializable
data class AiAdviceResponse(
    val messages: List<String>,
    val disclaimer: String? = "Not medical advice",
)
