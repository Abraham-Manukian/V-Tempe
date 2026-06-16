package com.vtempe.server.features.ai.data.service.split

import com.vtempe.server.features.ai.domain.model.MovementPattern
import com.vtempe.server.features.ai.domain.model.PatternSlot
import com.vtempe.server.features.ai.domain.model.WorkoutSkeleton

/**
 * Science-based split templates.
 * Nunes 2021: compound exercises first → better strength gains.
 * Grgic 2018: ≥2x/week per muscle for strength.
 */
internal object SplitTemplates {

    /** 1–2 training days/week. */
    fun fullBodyAB(p: SplitParams): List<WorkoutSkeleton> = listOf(
        skeleton(
            "Full Body A", p,
            compounds  = listOf(MovementPattern.KNEE_DOMINANT, MovementPattern.HORIZONTAL_PUSH, MovementPattern.VERTICAL_PULL),
            isolations = listOf(MovementPattern.CORE)
        ),
        skeleton(
            "Full Body B", p,
            compounds  = listOf(MovementPattern.HINGE, MovementPattern.HORIZONTAL_PULL, MovementPattern.VERTICAL_PUSH),
            isolations = listOf(MovementPattern.CORE)
        )
    )

    /** 3 training days/week — A→B→A rotation (each muscle hit 2x). */
    fun fullBodyABA(p: SplitParams): List<WorkoutSkeleton> {
        val a = skeleton(
            "Full Body A", p,
            compounds  = listOf(MovementPattern.KNEE_DOMINANT, MovementPattern.HORIZONTAL_PUSH, MovementPattern.VERTICAL_PULL),
            isolations = listOf(MovementPattern.CORE)
        )
        val b = skeleton(
            "Full Body B", p,
            compounds  = listOf(MovementPattern.HINGE, MovementPattern.HORIZONTAL_PULL, MovementPattern.VERTICAL_PUSH),
            isolations = listOf(MovementPattern.SINGLE_LEG)
        )
        return listOf(a, b, a)
    }

    /** 4 training days/week. */
    fun upperLower(p: SplitParams): List<WorkoutSkeleton> = listOf(
        skeleton("Upper A", p, listOf(MovementPattern.HORIZONTAL_PUSH, MovementPattern.VERTICAL_PULL), listOf(MovementPattern.ARM_FLEXION)),
        skeleton("Lower A", p, listOf(MovementPattern.KNEE_DOMINANT, MovementPattern.HINGE), listOf(MovementPattern.CORE)),
        skeleton("Upper B", p, listOf(MovementPattern.VERTICAL_PUSH, MovementPattern.HORIZONTAL_PULL), listOf(MovementPattern.ARM_EXTENSION)),
        skeleton("Lower B", p, listOf(MovementPattern.SINGLE_LEG, MovementPattern.HINGE), listOf(MovementPattern.CORE))
    )

    /** 5–6 training days/week — Push/Pull/Legs repeated as needed. */
    fun ppl(p: SplitParams, dayCount: Int): List<WorkoutSkeleton> {
        val base = listOf(
            skeleton("Push", p, listOf(MovementPattern.HORIZONTAL_PUSH, MovementPattern.VERTICAL_PUSH), listOf(MovementPattern.ARM_EXTENSION)),
            skeleton("Pull", p, listOf(MovementPattern.VERTICAL_PULL, MovementPattern.HORIZONTAL_PULL), listOf(MovementPattern.ARM_FLEXION)),
            skeleton("Legs", p, listOf(MovementPattern.KNEE_DOMINANT, MovementPattern.HINGE), listOf(MovementPattern.SINGLE_LEG, MovementPattern.CORE))
        )
        return (base + base).take(dayCount)
    }

    // ── Private ──────────────────────────────────────────────────────────────

    private fun skeleton(
        label: String,
        p: SplitParams,
        compounds: List<MovementPattern>,
        isolations: List<MovementPattern>
    ): WorkoutSkeleton {
        val slots = (compoundSlots(compounds, p) + isolationSlots(isolations, p))
            .take(p.exercisesPerSession)
        return WorkoutSkeleton(label = label, slots = slots)
    }

    private fun compoundSlots(patterns: List<MovementPattern>, p: SplitParams) =
        patterns.map { PatternSlot(it, p.setsCompound, p.compoundRepMin, p.compoundRepMax, p.rpeCompound, p.restCompoundSeconds) }

    private fun isolationSlots(patterns: List<MovementPattern>, p: SplitParams) =
        patterns.map { PatternSlot(it, p.setsIsolation, p.isolationRepMin, p.isolationRepMax, p.rpeIsolation, p.restIsolationSeconds) }
}
