package com.vtempe.server.shared.dto.training

import com.vtempe.server.shared.dto.profile.AiProfile
import kotlinx.serialization.Serializable

@Serializable
data class AiTrainingRequest(
    val profile: AiProfile,
    val weekIndex: Int,
    val locale: String? = null,
)

@Serializable
data class AiSet(
    val exerciseId: String,
    val reps: Int,
    val weightKg: Double? = null,
    val rpe: Double? = null,
)

@Serializable
data class AiWorkout(
    val id: String,
    val date: String,
    val sets: List<AiSet>,
)

@Serializable
data class AiTrainingResponse(
    val weekIndex: Int,
    val workouts: List<AiWorkout>,
)
