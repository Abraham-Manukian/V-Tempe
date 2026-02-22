package com.vtempe.server.shared.dto.training

import kotlinx.serialization.Serializable

@Serializable
data class AiTrainingResponse(
    val weekIndex: Int,
    val workouts: List<AiWorkout>,
)
