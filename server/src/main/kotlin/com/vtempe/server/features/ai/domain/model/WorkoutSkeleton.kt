package com.vtempe.server.features.ai.domain.model

data class PatternSlot(
    val pattern: MovementPattern,
    val sets: Int,
    val repMin: Int,
    val repMax: Int,
    val rpeTarget: Float,
    val restSeconds: Int
)

data class WorkoutSkeleton(
    val label: String,
    val slots: List<PatternSlot>
)
