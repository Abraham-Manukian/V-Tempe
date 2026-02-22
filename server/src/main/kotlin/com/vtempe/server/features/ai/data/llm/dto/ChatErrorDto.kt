package com.vtempe.server.features.ai.data.llm.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive

@Serializable
data class ChatErrorDto(
    val message: String,
    val code: JsonElement? = null,
    @SerialName("type") val type: String? = null,
) {
    val codeAsString: String?
        get() = (code as? JsonPrimitive)?.content
}
