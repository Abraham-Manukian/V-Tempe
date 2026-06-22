package com.vtempe.server.shared.dto.profile

import kotlinx.serialization.Serializable

@Serializable
data class AiExercisePerformance(
    val exerciseId: String,
    val weightKg: Double?,
    val reps: Int,
)
