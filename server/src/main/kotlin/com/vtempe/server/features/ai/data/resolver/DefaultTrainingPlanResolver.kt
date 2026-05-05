package com.vtempe.server.features.ai.data.resolver

import com.vtempe.server.features.ai.domain.model.MovementPattern
import com.vtempe.server.features.ai.domain.model.TrainingMode
import com.vtempe.server.features.ai.domain.port.ExerciseCatalog
import com.vtempe.server.features.ai.domain.port.TrainingPlanResolver
import com.vtempe.server.features.ai.data.service.normalizeExerciseToken
import kotlin.math.absoluteValue

class DefaultTrainingPlanResolver(
    private val exerciseCatalog: ExerciseCatalog
) : TrainingPlanResolver {

    override fun resolveMode(trainingModeRaw: String?, equipment: List<String>): TrainingMode {
        val explicit = TrainingMode.fromWire(trainingModeRaw)
        if (explicit != TrainingMode.AUTO) return explicit

        val normalizedEquipment = normalizeEquipment(equipment)
        val hasGymGear = normalizedEquipment.any { it in setOf("barbell", "bench") }
        val hasHomeGear = normalizedEquipment.any { it in setOf("dumbbells", "bands", "kettlebell", "pullup_bar", "trx", "mat") }
        val hasCardio = normalizedEquipment.contains("cardio")

        return when {
            hasGymGear && hasHomeGear -> TrainingMode.MIXED
            hasGymGear -> TrainingMode.GYM
            hasHomeGear -> TrainingMode.HOME
            hasCardio -> TrainingMode.OUTDOOR
            else -> TrainingMode.HOME
        }
    }

    override fun normalizeEquipment(equipment: List<String>): Set<String> =
        equipment.mapNotNull(::canonicalEquipmentTag).toSet()

    override fun resolveExerciseId(
        rawToken: String,
        trainingModeRaw: String?,
        equipment: List<String>,
        usedExerciseIds: Set<String>,
        rotationSeed: Int
    ): String? {
        val token = normalizeExerciseToken(rawToken)
        if (token.isBlank()) return null

        val mode = resolveMode(trainingModeRaw, equipment)
        val normalizedEquipment = normalizeEquipment(equipment)

        exerciseCatalog.findByIdOrAlias(token)?.let { explicit ->
            val compatibleCandidates = exerciseCatalog.candidatesFor(explicit.primaryPattern, mode, normalizedEquipment)
            val concreteCompatible = compatibleCandidates.any { it.id == explicit.id }
            return if (
                concreteCompatible &&
                explicit.id !in usedExerciseIds &&
                !shouldPreferResolverCandidateOverExplicit(
                    explicit = explicit,
                    pattern = explicit.primaryPattern,
                    mode = mode,
                    compatibleCandidates = compatibleCandidates
                )
            ) {
                explicit.id
            } else {
                selectCandidate(explicit.primaryPattern, mode, normalizedEquipment, usedExerciseIds, rotationSeed)
            }
        }

        val pattern = MovementPattern.fromToken(token) ?: return null
        return selectCandidate(pattern, mode, normalizedEquipment, usedExerciseIds, rotationSeed)
    }

    private fun selectCandidate(
        pattern: MovementPattern,
        mode: TrainingMode,
        normalizedEquipment: Set<String>,
        usedExerciseIds: Set<String>,
        rotationSeed: Int
    ): String? {
        val candidates = exerciseCatalog.candidatesFor(pattern, mode, normalizedEquipment)
        if (candidates.isEmpty()) return null

        val freshCandidates = candidates.filterNot { usedExerciseIds.contains(it.id) }
        val pool = freshCandidates.ifEmpty { candidates }
        val ranked = pool
            .map { candidate -> candidateScore(candidate, pattern, mode, pool) to candidate }
            .sortedBy { it.first }

        val bestScore = ranked.first().first
        val finalists = ranked.filter { it.first == bestScore }.map { it.second }
        val index = rotationSeed.absoluteValue % finalists.size
        return finalists[index].id
    }

    private fun candidateScore(
        candidate: com.vtempe.server.features.ai.domain.model.ExerciseCatalogItem,
        pattern: MovementPattern,
        mode: TrainingMode,
        pool: List<com.vtempe.server.features.ai.domain.model.ExerciseCatalogItem>
    ): Int {
        var score = candidate.priority

        val shouldPreferEquipmentBacked =
            (mode == TrainingMode.GYM || mode == TrainingMode.MIXED) &&
                pattern in setOf(
                    MovementPattern.HORIZONTAL_PUSH,
                    MovementPattern.HORIZONTAL_PULL,
                    MovementPattern.VERTICAL_PUSH,
                    MovementPattern.VERTICAL_PULL,
                    MovementPattern.ARM_FLEXION,
                    MovementPattern.ARM_EXTENSION,
                    MovementPattern.CONDITIONING
                )

        if (shouldPreferEquipmentBacked) {
            val hasEquipmentBackedAlternative = pool.any { it.requiredEquipment.isNotEmpty() }
            if (hasEquipmentBackedAlternative && candidate.requiredEquipment.isEmpty()) {
                score += 100
            }
        }

        return score
    }

    private fun shouldPreferResolverCandidateOverExplicit(
        explicit: com.vtempe.server.features.ai.domain.model.ExerciseCatalogItem,
        pattern: MovementPattern,
        mode: TrainingMode,
        compatibleCandidates: List<com.vtempe.server.features.ai.domain.model.ExerciseCatalogItem>
    ): Boolean {
        if (mode != TrainingMode.GYM && mode != TrainingMode.MIXED) return false
        val hasEquipmentBackedAlternative = compatibleCandidates.any { it.requiredEquipment.isNotEmpty() }
        if (!hasEquipmentBackedAlternative) return false

        val prefersEquipmentBacked =
            pattern in setOf(
                MovementPattern.HORIZONTAL_PUSH,
                MovementPattern.HORIZONTAL_PULL,
                MovementPattern.VERTICAL_PUSH,
                MovementPattern.VERTICAL_PULL,
                MovementPattern.ARM_FLEXION,
                MovementPattern.ARM_EXTENSION,
                MovementPattern.CONDITIONING
            )

        return prefersEquipmentBacked && explicit.requiredEquipment.isEmpty()
    }

    private fun canonicalEquipmentTag(raw: String): String? {
        val token = normalizeExerciseToken(raw)
        if (token.isBlank()) return null

        return when {
            token.contains("dumbbell") || token.contains("гантел") -> "dumbbells"
            token.contains("barbell") || token.contains("штанг") -> "barbell"
            token.contains("kettlebell") || token.contains("гир") -> "kettlebell"
            token.contains("band") || token.contains("резин") || token.contains("эспанд") -> "bands"
            token.contains("bench") || token.contains("скам") -> "bench"
            token.contains("pullup_bar") || token.contains("pull_up_bar") || token.contains("pullup") ||
                token.contains("турник") || token.contains("переклад") -> "pullup_bar"
            token.contains("trx") -> "trx"
            token.contains("mat") || token.contains("коврик") -> "mat"
            token.contains("cardio") || token.contains("кардио") ||
                token.contains("bike") || token.contains("вел") ||
                token.contains("run") || token.contains("бег") ||
                token.contains("treadmill") || token.contains("дорожк") -> "cardio"
            else -> token
        }
    }
}
