package com.vtempe.server.features.ai.data.llm.dto

import kotlinx.serialization.Serializable

@Serializable
data class ChatMessageDto(
    val role: String,
    val content: String,
)
