package com.vtempe.server.shared.dto.profile

import kotlinx.serialization.Serializable

@Serializable
data class AiRecentWorkout(
    val date: String,
    val completionRate: Double,
    val completedItems: Int,
    val plannedItems: Int,
    val totalVolumeKg: Double,
    val averageRpe: Double? = null,
    val notes: String = ""
)
