package com.vtempe.server.features.ai.data.catalog

import com.vtempe.server.features.ai.domain.model.ExerciseCatalogItem
import com.vtempe.server.features.ai.domain.model.MovementPattern
import com.vtempe.server.features.ai.domain.model.TrainingMode
import com.vtempe.server.features.ai.domain.port.ExerciseCatalog

class BuiltInExerciseCatalog : ExerciseCatalog {

    private val items = listOf(
        ExerciseCatalogItem(
            id = "squat",
            aliases = setOf("back_squat"),
            primaryPattern = MovementPattern.KNEE_DOMINANT,
            supportedModes = setOf(TrainingMode.GYM, TrainingMode.MIXED),
            requiredEquipment = setOf("barbell", "rack"),
            priority = 10
        ),
        ExerciseCatalogItem(
            id = "leg_press",
            aliases = setOf("legpress"),
            primaryPattern = MovementPattern.KNEE_DOMINANT,
            supportedModes = setOf(TrainingMode.GYM, TrainingMode.MIXED),
            priority = 20
        ),
        ExerciseCatalogItem(
            id = "lunge",
            aliases = setOf("walking_lunge"),
            primaryPattern = MovementPattern.SINGLE_LEG,
            secondaryPatterns = setOf(MovementPattern.KNEE_DOMINANT),
            supportedModes = setOf(TrainingMode.GYM, TrainingMode.HOME, TrainingMode.OUTDOOR, TrainingMode.MIXED),
            priority = 30
        ),
        ExerciseCatalogItem(
            id = "deadlift",
            primaryPattern = MovementPattern.HINGE,
            supportedModes = setOf(TrainingMode.GYM, TrainingMode.MIXED),
            requiredEquipment = setOf("barbell"),
            priority = 10
        ),
        ExerciseCatalogItem(
            id = "hip_thrust",
            aliases = setOf("hipthrust", "hip_thrusts"),
            primaryPattern = MovementPattern.HINGE,
            supportedModes = setOf(TrainingMode.GYM, TrainingMode.HOME, TrainingMode.MIXED),
            priority = 20
        ),
        ExerciseCatalogItem(
            id = "bench",
            aliases = setOf("bench_press"),
            primaryPattern = MovementPattern.HORIZONTAL_PUSH,
            supportedModes = setOf(TrainingMode.GYM, TrainingMode.MIXED),
            requiredEquipment = setOf("bench", "barbell", "dumbbells"),
            priority = 10
        ),
        ExerciseCatalogItem(
            id = "pushup",
            aliases = setOf("push_up", "push_ups"),
            primaryPattern = MovementPattern.HORIZONTAL_PUSH,
            supportedModes = setOf(TrainingMode.GYM, TrainingMode.HOME, TrainingMode.OUTDOOR, TrainingMode.MIXED),
            priority = 20
        ),
        ExerciseCatalogItem(
            id = "dip",
            aliases = setOf("parallel_bar_dip", "parallel_bar_dips"),
            primaryPattern = MovementPattern.HORIZONTAL_PUSH,
            secondaryPatterns = setOf(MovementPattern.ARM_EXTENSION),
            supportedModes = setOf(TrainingMode.GYM, TrainingMode.HOME, TrainingMode.OUTDOOR, TrainingMode.MIXED),
            requiredEquipment = setOf("bench", "trx", "pullup_bar"),
            priority = 30
        ),
        ExerciseCatalogItem(
            id = "row",
            aliases = setOf("bent_over_row", "barbell_row"),
            primaryPattern = MovementPattern.HORIZONTAL_PULL,
            supportedModes = setOf(TrainingMode.GYM, TrainingMode.HOME, TrainingMode.MIXED),
            requiredEquipment = setOf("barbell", "dumbbells", "bands", "trx"),
            priority = 10
        ),
        ExerciseCatalogItem(
            id = "pullup",
            aliases = setOf("pull_up", "pullups"),
            primaryPattern = MovementPattern.VERTICAL_PULL,
            supportedModes = setOf(TrainingMode.GYM, TrainingMode.HOME, TrainingMode.OUTDOOR, TrainingMode.MIXED),
            requiredEquipment = setOf("pullup_bar", "trx"),
            priority = 10
        ),
        ExerciseCatalogItem(
            id = "ohp",
            aliases = setOf("overhead_press"),
            primaryPattern = MovementPattern.VERTICAL_PUSH,
            supportedModes = setOf(TrainingMode.GYM, TrainingMode.HOME, TrainingMode.MIXED),
            requiredEquipment = setOf("barbell", "dumbbells", "bands", "kettlebell"),
            priority = 10
        ),
        ExerciseCatalogItem(
            id = "curl",
            aliases = setOf("bicep_curl", "biceps_curl", "biceps_curls"),
            primaryPattern = MovementPattern.ARM_FLEXION,
            supportedModes = setOf(TrainingMode.GYM, TrainingMode.HOME, TrainingMode.MIXED),
            requiredEquipment = setOf("barbell", "dumbbells", "bands", "kettlebell"),
            priority = 10
        ),
        ExerciseCatalogItem(
            id = "tricep_extension",
            aliases = setOf("triceps_extension", "triceps_extensions"),
            primaryPattern = MovementPattern.ARM_EXTENSION,
            supportedModes = setOf(TrainingMode.GYM, TrainingMode.HOME, TrainingMode.MIXED),
            requiredEquipment = setOf("barbell", "dumbbells", "bands", "kettlebell"),
            priority = 10
        ),
        ExerciseCatalogItem(
            id = "plank",
            aliases = setOf("plank_hold"),
            primaryPattern = MovementPattern.CORE,
            supportedModes = setOf(TrainingMode.GYM, TrainingMode.HOME, TrainingMode.OUTDOOR, TrainingMode.MIXED),
            priority = 10
        ),
        ExerciseCatalogItem(
            id = "run",
            aliases = setOf("running"),
            primaryPattern = MovementPattern.CONDITIONING,
            supportedModes = setOf(TrainingMode.OUTDOOR, TrainingMode.HOME, TrainingMode.MIXED),
            priority = 10
        ),
        ExerciseCatalogItem(
            id = "bike",
            aliases = setOf("cycling"),
            primaryPattern = MovementPattern.CONDITIONING,
            supportedModes = setOf(TrainingMode.GYM, TrainingMode.HOME, TrainingMode.MIXED),
            requiredEquipment = setOf("cardio"),
            priority = 20
        ),
        ExerciseCatalogItem(
            id = "yoga",
            primaryPattern = MovementPattern.MOBILITY,
            secondaryPatterns = setOf(MovementPattern.CORE),
            supportedModes = setOf(TrainingMode.GYM, TrainingMode.HOME, TrainingMode.OUTDOOR, TrainingMode.MIXED),
            priority = 10
        )
    )

    private val canonicalIndex = items.associateBy { it.id }
    private val aliasIndex = items
        .flatMap { item -> item.aliases.map { alias -> normalizeCatalogToken(alias) to item } }
        .toMap()

    override fun all(): List<ExerciseCatalogItem> = items

    override fun supportedExerciseIds(): Set<String> = canonicalIndex.keys

    override fun findByIdOrAlias(rawToken: String): ExerciseCatalogItem? {
        val normalized = normalizeCatalogToken(rawToken)
        return canonicalIndex[normalized] ?: aliasIndex[normalized]
    }

    override fun availablePatterns(mode: TrainingMode, equipment: Set<String>): List<MovementPattern> =
        MovementPattern.entries.filter { pattern ->
            candidatesFor(pattern, mode, equipment).isNotEmpty()
        }

    override fun candidatesFor(
        pattern: MovementPattern,
        mode: TrainingMode,
        equipment: Set<String>
    ): List<ExerciseCatalogItem> {
        val scoped = items.filter { it.supports(pattern) }
        val byModeAndEquipment = scoped.filter { item ->
            supportsMode(item, mode) && matchesEquipment(item, mode, equipment)
        }
        if (byModeAndEquipment.isNotEmpty()) return byModeAndEquipment.sortedBy { it.priority }

        if (mode == TrainingMode.HOME) return emptyList()

        val relaxedEquipment = scoped.filter { item -> supportsMode(item, mode) }
        if (relaxedEquipment.isNotEmpty()) return relaxedEquipment.sortedBy { it.priority }

        val relaxedMode = scoped.filter { item -> matchesEquipment(item, mode, equipment) }
        if (relaxedMode.isNotEmpty()) return relaxedMode.sortedBy { it.priority }

        return scoped.sortedBy { it.priority }
    }

    private fun supportsMode(item: ExerciseCatalogItem, mode: TrainingMode): Boolean =
        when (mode) {
            TrainingMode.AUTO, TrainingMode.MIXED -> true
            else -> item.supportedModes.contains(mode) || item.supportedModes.contains(TrainingMode.MIXED)
        }

    private fun matchesEquipment(
        item: ExerciseCatalogItem,
        mode: TrainingMode,
        equipment: Set<String>
    ): Boolean {
        if (item.requiredEquipment.isEmpty()) return true
        if (mode == TrainingMode.GYM || mode == TrainingMode.OUTDOOR || mode == TrainingMode.MIXED) return true
        return equipment.any(item.requiredEquipment::contains)
    }

    private fun normalizeCatalogToken(rawToken: String): String =
        rawToken
            .trim()
            .lowercase()
            .replace(' ', '_')
            .replace('-', '_')
            .trim('_')
}
