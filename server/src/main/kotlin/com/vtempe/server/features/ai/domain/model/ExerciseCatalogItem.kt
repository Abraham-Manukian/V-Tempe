package com.vtempe.server.features.ai.domain.model

/** What unit the AI-written `reps` field holds for this exercise (see clampRepsForUnit). */
enum class DurationUnit { REPS, SECONDS, MINUTES }

/**
 * @param difficulty 1 = any beginner can do it; 5 = elite / requires years of training.
 *   Matched against AiProfile.experienceLevel (1–5) in the resolver so novices don't
 *   receive muscle-ups, pistol squats, or handstand push-ups.
 * @param isBodyweightOnly true if external weight (barbell/dumbbell) should never be added —
 *   normalizeTrainingPlan nulls out weightKg for these instead of trusting an AI-invented number.
 * @param durationUnit whether the AI's `reps` value for this exercise is a rep count (default),
 *   a hold time in seconds, or a duration in minutes — normalizeTrainingPlan clamps into the
 *   matching sane range instead of trusting whatever the AI wrote (item A2, was 3 separate
 *   hardcoded ID sets in AiTrainingPlanPolicy.kt that drifted from this catalog over time).
 */
data class ExerciseCatalogItem(
    val id: String,
    val aliases: Set<String> = emptySet(),
    val primaryPattern: MovementPattern,
    val secondaryPatterns: Set<MovementPattern> = emptySet(),
    val supportedModes: Set<TrainingMode>,
    val requiredEquipment: Set<String> = emptySet(),
    val priority: Int = 100,
    val difficulty: Int = 2,   // 1 beginner … 5 elite
    val isBodyweightOnly: Boolean = false,
    val durationUnit: DurationUnit = DurationUnit.REPS
) {
    fun supports(pattern: MovementPattern): Boolean =
        primaryPattern == pattern || secondaryPatterns.contains(pattern)
}
