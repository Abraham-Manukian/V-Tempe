package com.vtempe.server.shared.dto.training

import com.vtempe.server.shared.dto.profile.AiProfile
import kotlinx.serialization.Serializable

@Serializable
data class AiTrainingRequest(
    val profile: AiProfile,
    val weekIndex: Int,
    val locale: String? = null,
)
