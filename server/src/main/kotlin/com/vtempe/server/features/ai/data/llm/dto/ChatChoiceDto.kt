package com.vtempe.server.features.ai.data.llm.dto

import kotlinx.serialization.Serializable

@Serializable
data class ChatChoiceDto(
    val message: ChatMessageDto? = null,
)
