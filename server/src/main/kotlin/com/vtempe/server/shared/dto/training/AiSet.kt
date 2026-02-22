package com.vtempe.server.shared.dto.training

import kotlinx.serialization.Serializable

@Serializable
data class AiSet(
    val exerciseId: String,
    val reps: Int,
    val weightKg: Double? = null,
    val rpe: Double? = null,
)
