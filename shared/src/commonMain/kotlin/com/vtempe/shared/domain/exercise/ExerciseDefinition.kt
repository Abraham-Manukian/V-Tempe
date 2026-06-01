package com.vtempe.shared.domain.exercise

import kotlinx.serialization.Serializable

@Serializable
data class ExerciseDefinition(
    val id: String,
    val aliases: Set<String> = emptySet(),
    val name: LocalizedText,
    val muscleGroups: List<String>,
    val difficulty: Int,
    val visualFamily: ExerciseVisualFamily,
    val calibrationKind: ExerciseCalibrationKind,
    val calibrationHint: LocalizedText,
    val imagePrompt: String,
    val technique: ExerciseTechnique
) {
    fun supports(rawExerciseId: String): Boolean {
        val normalized = normalizeExerciseId(rawExerciseId)
        return id == normalized || aliases.any { normalizeExerciseId(it) == normalized }
    }
}

internal fun normalizeExerciseId(rawExerciseId: String): String =
    rawExerciseId.trim().lowercase().replace('-', '_').replace(' ', '_')
