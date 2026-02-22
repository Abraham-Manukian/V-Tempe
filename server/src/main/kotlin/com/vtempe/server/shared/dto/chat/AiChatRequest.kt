package com.vtempe.server.shared.dto.chat

import com.vtempe.server.shared.dto.profile.AiProfile
import kotlinx.serialization.Serializable

@Serializable
data class AiChatRequest(
    val profile: AiProfile,
    val messages: List<AiChatMessage>,
    val locale: String? = null,
)
