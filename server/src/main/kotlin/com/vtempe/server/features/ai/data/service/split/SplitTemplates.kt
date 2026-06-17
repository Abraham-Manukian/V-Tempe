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

    /** 3 training days/week — A→B→A2 rotation each muscle hit ≥2x with exercise variety. */
    fun fullBodyABA(p: SplitParams): List<WorkoutSkeleton> {
        val (a, b) = fullBodyAB(p)
        // A2: same muscles as A but swap vertical pull → horizontal pull, vertical push → horizontal push
        // so AI is forced to choose different exercises (e.g. cable row instead of pull-up)
        val a2 = skeleton(
            "Full Body A2", p,
            compounds = listOf(
                MovementPattern.KNEE_DOMINANT,   // squat variation (can be different: leg press, goblet squat)
                MovementPattern.HORIZONTAL_PUSH, // incline / dumbbell press (different angle from A)
                MovementPattern.HORIZONTAL_PULL, // cable row / T-bar row (vs pull-up in A)
                MovementPattern.HINGE,           // good morning / Nordic curl variation
                MovementPattern.VERTICAL_PULL,   // lat pulldown (vs pull-up in A)
            ),
            isolations = listOf(MovementPattern.ARM_FLEXION, MovementPattern.CORE)
        )
        return listOf(a, b, a2)
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

    /** 5–6 training days/week — Push/Pull/Legs with variation sessions for repeats. */
    fun ppl(p: SplitParams, dayCount: Int): List<WorkoutSkeleton> {
        val push1 = skeleton(
            "Push", p,
            compounds = listOf(
                MovementPattern.HORIZONTAL_PUSH, // flat bench press
                MovementPattern.VERTICAL_PUSH,   // barbell OHP
                MovementPattern.HORIZONTAL_PUSH, // incline DB press
            ),
            isolations = listOf(MovementPattern.ARM_EXTENSION, MovementPattern.CORE)
        )
        // Push2: swap incline → dips, add lateral raise angle
        val push2 = skeleton(
            "Push 2", p,
            compounds = listOf(
                MovementPattern.VERTICAL_PUSH,   // DB shoulder press (vs barbell in Push 1)
                MovementPattern.HORIZONTAL_PUSH, // dips / cable fly
                MovementPattern.VERTICAL_PUSH,   // lateral raise / front raise
            ),
            isolations = listOf(MovementPattern.ARM_EXTENSION, MovementPattern.CORE)
        )
        val pull1 = skeleton(
            "Pull", p,
            compounds = listOf(
                MovementPattern.VERTICAL_PULL,   // weighted pull-up
                MovementPattern.HORIZONTAL_PULL, // barbell row
                MovementPattern.VERTICAL_PULL,   // lat pulldown
            ),
            isolations = listOf(MovementPattern.ARM_FLEXION, MovementPattern.MOBILITY)
        )
        // Pull2: swap to cable-focused, face pulls for rear delt
        val pull2 = skeleton(
            "Pull 2", p,
            compounds = listOf(
                MovementPattern.HORIZONTAL_PULL, // cable seated row
                MovementPattern.VERTICAL_PULL,   // chin-up (supinated)
                MovementPattern.HORIZONTAL_PULL, // face pull / rear delt fly
            ),
            isolations = listOf(MovementPattern.ARM_FLEXION, MovementPattern.MOBILITY)
        )
        val legs = skeleton(
            "Legs", p,
            compounds = listOf(
                MovementPattern.KNEE_DOMINANT,   // back squat
                MovementPattern.HINGE,           // Romanian deadlift
                MovementPattern.SINGLE_LEG,      // Bulgarian split squat
            ),
            isolations = listOf(MovementPattern.CORE, MovementPattern.CONDITIONING)
        )
        // 5 days: P/Pu/L/P2/Pu2 — 6 days: P/Pu/L/P2/Pu2/L
        val rotation = listOf(push1, pull1, legs, push2, pull2, legs)
        return rotation.take(dayCount)
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
