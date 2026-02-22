package com.vtempe.server.shared.dto.bootstrap

import com.vtempe.server.shared.dto.profile.AiProfile
import kotlinx.serialization.Serializable

@Serializable
data class AiBootstrapRequest(
    val profile: AiProfile,
    val weekIndex: Int = 0,
    val locale: String? = null,
)
