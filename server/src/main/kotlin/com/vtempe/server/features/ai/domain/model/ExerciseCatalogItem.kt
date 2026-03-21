package com.vtempe.server.features.ai.domain.model

data class ExerciseCatalogItem(
    val id: String,
    val aliases: Set<String> = emptySet(),
    val primaryPattern: MovementPattern,
    val secondaryPatterns: Set<MovementPattern> = emptySet(),
    val supportedModes: Set<TrainingMode>,
    val requiredEquipment: Set<String> = emptySet(),
    val priority: Int = 100
) {
    fun supports(pattern: MovementPattern): Boolean =
        primaryPattern == pattern || secondaryPatterns.contains(pattern)
}
