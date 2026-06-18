package com.vtempe.server.features.ai.data.service

import com.vtempe.server.features.ai.domain.port.ExerciseCatalog
import com.vtempe.server.features.ai.domain.port.TrainingPlanResolver

/**
 * Description-only variant: lists exercise patterns and examples without instructing the AI
 * to use slot tokens. Used in the bundle prompt where exercises are already pre-resolved.
 */
internal fun buildTrainingResolverDescriptions(
    exerciseCatalog: ExerciseCatalog,
    trainingPlanResolver: TrainingPlanResolver,
    trainingModeRaw: String?,
    equipment: List<String>
): String {
    val mode = trainingPlanResolver.resolveMode(trainingModeRaw, equipment)
    val normalizedEquipment = trainingPlanResolver.normalizeEquipment(equipment)
    val patterns = exerciseCatalog.availablePatterns(mode, normalizedEquipment)
        .ifEmpty { com.vtempe.server.features.ai.domain.model.MovementPattern.entries }
    val guide = patterns.joinToString(separator = "\n") { pattern ->
        val examples = exerciseCatalog.candidatesFor(pattern, mode, normalizedEquipment)
            .take(3)
            .joinToString(", ") { it.id }
        "- ${pattern.token}: ${pattern.promptDescription}. Typical exercises for this mode: $examples."
    }
    return "AVAILABLE EXERCISE PATTERNS (reference only — use IDs from the skeleton above):\n$guide"
}

internal fun buildTrainingResolverPrompt(
    exerciseCatalog: ExerciseCatalog,
    trainingPlanResolver: TrainingPlanResolver,
    trainingModeRaw: String?,
    equipment: List<String>
): String {
    val mode = trainingPlanResolver.resolveMode(trainingModeRaw, equipment)
    val normalizedEquipment = trainingPlanResolver.normalizeEquipment(equipment)
    val patterns = exerciseCatalog.availablePatterns(mode, normalizedEquipment)
        .ifEmpty { com.vtempe.server.features.ai.domain.model.MovementPattern.entries }

    val header = buildString {
        appendLine("- The `exerciseId` field is a resolver slot token, not a literal exercise catalog id.")
        appendLine("- Use ONLY these slot tokens in `exerciseId`: ${patterns.joinToString(prefix = "[", postfix = "]") { it.token }}.")
        appendLine("- The backend resolver will map those tokens to supported concrete exercises using trainingMode=${mode.wireValue} and the available equipment.")
        appendLine("- Prefer varied tokens across the week. Never repeat the same slot token inside one workout unless the user explicitly needs repeat work.")
    }

    val guide = patterns.joinToString(separator = "\n") { pattern ->
        val examples = exerciseCatalog.candidatesFor(pattern, mode, normalizedEquipment)
            .take(3)
            .joinToString(", ") { it.id }
        "- ${pattern.token}: ${pattern.promptDescription}. Typical resolved exercises: $examples."
    }

    return "$header$guide"
}
