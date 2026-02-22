package com.vtempe.server.features.ai.data.llm.dto

import kotlinx.serialization.Serializable

@Serializable
data class ChatCompletionResponseDto(
    val choices: List<ChatChoiceDto> = emptyList(),
    val error: ChatErrorDto? = null,
)
