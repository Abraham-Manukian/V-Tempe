package com.vtempe.server.shared.dto.chat

import kotlinx.serialization.Serializable

@Serializable
data class AiChatMessage(
    val role: String,
    val content: String,
)
