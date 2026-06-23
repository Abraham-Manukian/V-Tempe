package com.vtempe.server.features.ai.data.service.split

import com.vtempe.server.features.ai.domain.model.MovementPattern
import com.vtempe.server.features.ai.domain.model.PatternSlot
import com.vtempe.server.features.ai.domain.model.SlotType
import com.vtempe.server.features.ai.domain.model.WorkoutSkeleton

/**
 * Science-based split templates.
 * Nunes 2021: compound exercises first → better strength gains.
 * Grgic 2018: ≥2x/week per muscle for hypertrophy.
 * Schoenfeld 2017: hit each muscle from multiple angles per session.
 *
 * Slot assignment: first [primarySlotCount] compounds → PRIMARY tier,
 * remaining compounds → SECONDARY, all isolations → ISOLATION.
 */
internal object SplitTemplates {

    /** 1–2 training days/week — each session covers ALL major muscle groups. */
    fun fullBodyAB(p: SplitParams): List<WorkoutSkeleton> = listOf(
        skeleton(
            "Full Body A", p,
            compounds = listOf(
                MovementPattern.KNEE_DOMINANT,   // squat — quads primary
                MovementPattern.HORIZONTAL_PUSH, // bench press — chest + triceps
                MovementPattern.VERTICAL_PULL,   // pull-up / pulldown — lats
                MovementPattern.HINGE,           // RDL — hamstrings + glutes
                MovementPattern.VERTICAL_PUSH,   // OHP — shoulders
            ),
            isolations = listOf(MovementPattern.ARM_FLEXION, MovementPattern.CORE)
        ),
        skeleton(
            "Full Body B", p,
            compounds = listOf(
                MovementPattern.HINGE,           // deadlift — hamstrings primary
                MovementPattern.HORIZONTAL_PULL, // barbell/cable row — upper back
                MovementPattern.KNEE_DOMINANT,   // front squat / leg press — quads
                MovementPattern.HORIZONTAL_PUSH, // incline press — chest
                MovementPattern.VERTICAL_PULL,   // chin-up / lat pulldown — lats
            ),
            isolations = listOf(MovementPattern.ARM_EXTENSION, MovementPattern.CORE)
        )
    )

    /** 3 training days/week — A→B→A2, each muscle hit ≥2x with exercise variety. */
    fun fullBodyABA(p: SplitParams): List<WorkoutSkeleton> {
        val (a, b) = fullBodyAB(p)
        val a2 = skeleton(
            "Full Body A2", p,
            compounds = listOf(
                MovementPattern.KNEE_DOMINANT,   // goblet squat / leg press (different from A)
                MovementPattern.HORIZONTAL_PUSH, // incline DB press (different angle)
                MovementPattern.HORIZONTAL_PULL, // cable row / T-bar (vs pull-up in A)
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
                MovementPattern.HORIZONTAL_PULL, // cable/barbell row
                MovementPattern.VERTICAL_PUSH,   // OHP
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
                MovementPattern.VERTICAL_PUSH,   // OHP variation
                MovementPattern.HORIZONTAL_PULL, // row variation
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
        val push2 = skeleton(
            "Push 2", p,
            compounds = listOf(
                MovementPattern.VERTICAL_PUSH,   // DB shoulder press
                MovementPattern.HORIZONTAL_PUSH, // dips / cable fly
                MovementPattern.VERTICAL_PUSH,   // lateral raise variation
            ),
            isolations = listOf(MovementPattern.ARM_EXTENSION, MovementPattern.CORE)
        )
        val pull1 = skeleton(
            "Pull", p,
            compounds = listOf(
                MovementPattern.VERTICAL_PULL,   // pull-up — lats vertical
                MovementPattern.HORIZONTAL_PULL, // barbell/cable row — upper back
                MovementPattern.HORIZONTAL_PULL, // seated cable row / T-bar — mid back
            ),
            isolations = listOf(MovementPattern.ARM_FLEXION, MovementPattern.MOBILITY)
        )
        val pull2 = skeleton(
            "Pull 2", p,
            compounds = listOf(
                MovementPattern.VERTICAL_PULL,   // chin-up (supinated grip)
                MovementPattern.HORIZONTAL_PULL, // cable seated row
                MovementPattern.HORIZONTAL_PULL, // face pull / rear delt row
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
        // 5 days: legs must appear twice (Grgic 2018: ≥2×/week per muscle).
        // pull2 gets dropped — push/pull balance still maintained via push2.
        val rotation = if (dayCount == 5) {
            listOf(push1, pull1, legs, push2, legs)
        } else {
            listOf(push1, pull1, legs, push2, pull2, legs)
        }
        return rotation.take(dayCount)
    }

    /**
     * Bro split — each session targets one or two specific muscle groups.
     * 3 days: Chest+Triceps / Back+Biceps / Legs+Shoulders
     * 4 days: Chest+Triceps / Back+Biceps / Legs / Shoulders+Arms
     * 5 days: Chest / Back / Legs / Shoulders / Arms+Abs
     * 6 days: 3-day rotation × 2
     */
    fun broSplit(p: SplitParams, dayCount: Int): List<WorkoutSkeleton> {
        val chestTri = skeleton(
            "Chest + Triceps", p,
            compounds  = listOf(
                MovementPattern.HORIZONTAL_PUSH, // flat bench
                MovementPattern.HORIZONTAL_PUSH, // incline press
            ),
            isolations = listOf(MovementPattern.ARM_EXTENSION, MovementPattern.ARM_EXTENSION)
        )
        val backBi = skeleton(
            "Back + Biceps", p,
            compounds  = listOf(
                MovementPattern.VERTICAL_PULL,   // pull-up / pulldown
                MovementPattern.HORIZONTAL_PULL, // barbell / cable row
                MovementPattern.HORIZONTAL_PULL, // seated row variation
            ),
            isolations = listOf(MovementPattern.ARM_FLEXION)
        )
        val legsShoulders = skeleton(
            "Legs + Shoulders", p,
            compounds  = listOf(
                MovementPattern.KNEE_DOMINANT,   // squat
                MovementPattern.HINGE,           // RDL
                MovementPattern.VERTICAL_PUSH,   // OHP
            ),
            isolations = listOf(MovementPattern.SINGLE_LEG, MovementPattern.CORE)
        )
        val legs = skeleton(
            "Legs", p,
            compounds  = listOf(
                MovementPattern.KNEE_DOMINANT,
                MovementPattern.HINGE,
                MovementPattern.SINGLE_LEG,
            ),
            isolations = listOf(MovementPattern.CORE, MovementPattern.CONDITIONING)
        )
        val shouldersArms = skeleton(
            "Shoulders + Arms", p,
            compounds  = listOf(
                MovementPattern.VERTICAL_PUSH,   // OHP
                MovementPattern.VERTICAL_PUSH,   // lateral raise
            ),
            isolations = listOf(MovementPattern.ARM_FLEXION, MovementPattern.ARM_EXTENSION, MovementPattern.CORE)
        )
        val chest = skeleton(
            "Chest", p,
            compounds  = listOf(
                MovementPattern.HORIZONTAL_PUSH, // flat bench
                MovementPattern.HORIZONTAL_PUSH, // incline
                MovementPattern.HORIZONTAL_PUSH, // cable fly / dip
            ),
            isolations = listOf(MovementPattern.CORE)
        )
        val back = skeleton(
            "Back", p,
            compounds  = listOf(
                MovementPattern.VERTICAL_PULL,
                MovementPattern.HORIZONTAL_PULL,
                MovementPattern.HORIZONTAL_PULL,
            ),
            isolations = listOf(MovementPattern.MOBILITY)
        )
        val shoulders = skeleton(
            "Shoulders", p,
            compounds  = listOf(
                MovementPattern.VERTICAL_PUSH,
                MovementPattern.VERTICAL_PUSH,
            ),
            isolations = listOf(MovementPattern.CORE)
        )
        val armsAbs = skeleton(
            "Arms + Abs", p,
            compounds  = listOf(
                MovementPattern.ARM_FLEXION,
                MovementPattern.ARM_EXTENSION,
            ),
            isolations = listOf(MovementPattern.ARM_FLEXION, MovementPattern.ARM_EXTENSION, MovementPattern.CORE)
        )

        val rotation = when {
            dayCount <= 3 -> listOf(chestTri, backBi, legsShoulders)
            dayCount == 4 -> listOf(chestTri, backBi, legs, shouldersArms)
            dayCount == 5 -> listOf(chest, back, legs, shoulders, armsAbs)
            else          -> listOf(chestTri, backBi, legsShoulders, chestTri, backBi, legsShoulders)
        }
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
        patterns.mapIndexed { index, pattern ->
            val isPrimary = index < p.primarySlotCount
            PatternSlot(
                pattern     = pattern,
                slotType    = if (isPrimary) SlotType.PRIMARY else SlotType.SECONDARY,
                sets        = if (isPrimary) p.primarySets else p.secondarySets,
                repMin      = if (isPrimary) p.primaryRepMin else p.secondaryRepMin,
                repMax      = if (isPrimary) p.primaryRepMax else p.secondaryRepMax,
                rpeTarget   = if (isPrimary) p.primaryRpe else p.secondaryRpe,
                restSeconds = if (isPrimary) p.primaryRestSeconds else p.secondaryRestSeconds
            )
        }

    private fun isolationSlots(patterns: List<MovementPattern>, p: SplitParams) =
        patterns.map { pattern ->
            PatternSlot(
                pattern       = pattern,
                slotType      = SlotType.ISOLATION,
                sets          = p.isolationSets,
                repMin        = p.isolationRepMin,
                repMax        = p.isolationRepMax,
                rpeTarget     = p.isolationRpe,
                restSeconds   = p.isolationRestSeconds
            )
        }
}
