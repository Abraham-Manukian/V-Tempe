package com.vtempe.server.features.ai.data.llm.dto

import kotlinx.serialization.Serializable

@Serializable
data class ResponseFormatDto(
    val type: String
)
