package com.vtempe.server.features.ai.data.llm.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ChatCompletionRequestDto(
    val model: String,
    val messages: List<ChatMessageDto>,
    val temperature: Double? = null,
    @SerialName("top_p") val topP: Double? = null,
    @SerialName("max_tokens") val maxTokens: Int? = null,
    @SerialName("response_format") val responseFormat: ResponseFormatDto? = null,
    /** Sent together with [responseFormat] so OpenRouter never routes a schema-bearing request to
     *  an endpoint that would ignore the schema. See ProviderPreferencesDto. */
    val provider: ProviderPreferencesDto? = null
)
