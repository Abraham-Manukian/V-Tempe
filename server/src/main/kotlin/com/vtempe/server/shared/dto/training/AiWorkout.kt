package com.vtempe.server.shared.dto.training

import kotlinx.serialization.Serializable

@Serializable
data class AiWorkout(
    val id: String,
    val date: String,
    val sets: List<AiSet>,
)
