package com.vtempe.server.features.ai.data.service.split

import com.vtempe.server.features.ai.domain.model.MovementPattern
import com.vtempe.server.features.ai.domain.model.PatternSlot
import com.vtempe.server.features.ai.domain.model.WorkoutSkeleton

/**
 * Science-based split templates.
 * Nunes 2021: compound exercises first → better strength gains.
 * Grgic 2018: ≥2x/week per muscle for hypertrophy.
 * Schoenfeld 2017: hit each muscle from multiple angles per session.
 *
 * Full Body A/B:  each session covers ALL major muscle groups (quads, hamstrings,
 *                 chest, upper back, lats, shoulders + isolation).
 * Upper/Lower:    upper hits push+pull in both planes; lower hits knee+hip+single-leg.
 * PPL:            3 distinct stimuli, rotated 2x per 6-day week.
 */
internal object SplitTemplates {

    /** 1–2 training days/week — both sessions must be complete full-body stimulus. */
    fun fullBodyAB(p: SplitParams): List<WorkoutSkeleton> = listOf(
        skeleton(
            "Full Body A", p,
            compounds = listOf(
                MovementPattern.KNEE_DOMINANT,   // squat variation — quads primary
                MovementPattern.HORIZONTAL_PUSH, // bench / DB press — chest + triceps
                MovementPattern.VERTICAL_PULL,   // pull-up / pulldown — lats
                MovementPattern.HINGE,           // RDL / leg curl — hamstrings + glutes
                MovementPattern.VERTICAL_PUSH,   // OHP variation — shoulders
            ),
            isolations = listOf(MovementPattern.ARM_FLEXION, MovementPattern.CORE)
        ),
        skeleton(
            "Full Body B", p,
            compounds = listOf(
                MovementPattern.HINGE,           // deadlift variation — hamstrings primary
                MovementPattern.HORIZONTAL_PULL, // barbell/cable row — upper back + rhomboids
                MovementPattern.KNEE_DOMINANT,   // front squat / leg press — quads
                MovementPattern.HORIZONTAL_PUSH, // incline press / dips — chest
                MovementPattern.VERTICAL_PULL,   // chin-up / lat pulldown — lats
            ),
            isolations = listOf(MovementPattern.ARM_EXTENSION, MovementPattern.CORE)
        )
    )

    /** 3 training days/week — A→B→A rotation each muscle hit ≥2x. */
    fun fullBodyABA(p: SplitParams): List<WorkoutSkeleton> {
        val sessions = fullBodyAB(p)
        return listOf(sessions[0], sessions[1], sessions[0])
    }

    /** 4 training days/week — upper hits push+pull; lower hits knee+hip+accessory. */
    fun upperLower(p: SplitParams): List<WorkoutSkeleton> = listOf(
        skeleton(
            "Upper A", p,
            compounds = listOf(
                MovementPattern.HORIZONTAL_PUSH, // bench press
                MovementPattern.VERTICAL_PULL,   // pull-up / pulldown
                MovementPattern.HORIZONTAL_PULL, // cable/barbell row — upper back
                MovementPattern.VERTICAL_PUSH,   // OHP — shoulders
            ),
            isolations = listOf(MovementPattern.ARM_FLEXION, MovementPattern.ARM_EXTENSION)
        ),
        skeleton(
            "Lower A", p,
            compounds = listOf(
                MovementPattern.KNEE_DOMINANT,   // squat
                MovementPattern.HINGE,           // RDL
                MovementPattern.SINGLE_LEG,      // lunge / split squat
            ),
            isolations = listOf(MovementPattern.CORE, MovementPattern.CONDITIONING)
        ),
        skeleton(
            "Upper B", p,
            compounds = listOf(
                MovementPattern.VERTICAL_PUSH,   // OHP
                MovementPattern.HORIZONTAL_PULL, // row
                MovementPattern.HORIZONTAL_PUSH, // incline DB press
                MovementPattern.VERTICAL_PULL,   // chin-up
            ),
            isolations = listOf(MovementPattern.ARM_EXTENSION, MovementPattern.ARM_FLEXION)
        ),
        skeleton(
            "Lower B", p,
            compounds = listOf(
                MovementPattern.HINGE,           // deadlift
                MovementPattern.SINGLE_LEG,      // Bulgarian split squat
                MovementPattern.KNEE_DOMINANT,   // leg press / hack squat
            ),
            isolations = listOf(MovementPattern.CORE, MovementPattern.MOBILITY)
        )
    )

    /** 5–6 training days/week — Push/Pull/Legs repeated. */
    fun ppl(p: SplitParams, dayCount: Int): List<WorkoutSkeleton> {
        val base = listOf(
            skeleton(
                "Push", p,
                compounds = listOf(
                    MovementPattern.HORIZONTAL_PUSH, // bench press
                    MovementPattern.VERTICAL_PUSH,   // OHP
                    MovementPattern.HORIZONTAL_PUSH, // incline / dips
                ),
                isolations = listOf(MovementPattern.ARM_EXTENSION, MovementPattern.CORE)
            ),
            skeleton(
                "Pull", p,
                compounds = listOf(
                    MovementPattern.VERTICAL_PULL,   // pull-up
                    MovementPattern.HORIZONTAL_PULL, // barbell row
                    MovementPattern.VERTICAL_PULL,   // lat pulldown / cable row
                ),
                isolations = listOf(MovementPattern.ARM_FLEXION, MovementPattern.MOBILITY)
            ),
            skeleton(
                "Legs", p,
                compounds = listOf(
                    MovementPattern.KNEE_DOMINANT,   // squat
                    MovementPattern.HINGE,           // deadlift variation
                    MovementPattern.SINGLE_LEG,      // lunge / leg press
                ),
                isolations = listOf(MovementPattern.CORE, MovementPattern.CONDITIONING)
            )
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
