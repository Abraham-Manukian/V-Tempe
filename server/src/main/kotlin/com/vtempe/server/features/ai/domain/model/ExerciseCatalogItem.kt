package com.vtempe.server.features.ai.domain.model

/**
 * @param difficulty 1 = any beginner can do it; 5 = elite / requires years of training.
 *   Matched against AiProfile.experienceLevel (1–5) in the resolver so novices don't
 *   receive muscle-ups, pistol squats, or handstand push-ups.
 */
data class ExerciseCatalogItem(
    val id: String,
    val aliases: Set<String> = emptySet(),
    val primaryPattern: MovementPattern,
    val secondaryPatterns: Set<MovementPattern> = emptySet(),
    val supportedModes: Set<TrainingMode>,
    val requiredEquipment: Set<String> = emptySet(),
    val priority: Int = 100,
    val difficulty: Int = 2   // 1 beginner … 5 elite
) {
    fun supports(pattern: MovementPattern): Boolean =
        primaryPattern == pattern || secondaryPatterns.contains(pattern)
}
