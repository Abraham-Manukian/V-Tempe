package com.vtempe.server.features.ai.data.service.split

/**
 * Maps (goal, trainingFocus, experienceLevel, sessionDurationMins, weekIndex) → SplitParams.
 *
 * Three loading tiers per session (from PDF + ACSM):
 *   PRIMARY   = 4×6-8,  RPE 8.5, 3 min rest  — main compound accents
 *   SECONDARY = 3×8-12, RPE 7.5, 90s rest     — supporting compounds
 *   ISOLATION = 2×10-15, RPE 7.0, 60s rest    — accessories
 *
 * Beginners (exp 1-2): no PRIMARY tier — only SECONDARY+ISOLATION, linear progression.
 * Deload every 7th week: -35% volume, RPE capped at 7.
 */
internal object SplitParamsFactory {

    enum class Focus { STRENGTH, HYPERTROPHY, GENERAL, FAT_LOSS }
    enum class Goal  { GAIN_MUSCLE, LOSE_FAT, MAINTAIN }

    fun create(
        goal: Goal,
        focus: Focus,
        experienceLevel: Int,
        sessionDurationMins: Int,
        weekIndex: Int
    ): SplitParams {
        val isDeload   = weekIndex > 0 && weekIndex % 7 == 6
        val isBeginner = experienceLevel <= 2
        val exercises  = exercisesFromDuration(sessionDurationMins, isBeginner)
        val base       = baseParams(goal, focus, experienceLevel, weekIndex).copy(exercisesPerSession = exercises)
        return if (isDeload) deload(base) else base
    }

    fun focusFromRaw(raw: String): Focus =
        runCatching { Focus.valueOf(raw.uppercase()) }.getOrDefault(Focus.GENERAL)

    fun goalFromRaw(raw: String): Goal =
        runCatching { Goal.valueOf(raw.uppercase()) }.getOrDefault(Goal.MAINTAIN)

    // ── Private ──────────────────────────────────────────────────────────────

    private fun baseParams(goal: Goal, focus: Focus, exp: Int, week: Int): SplitParams {
        val isBeginner = exp <= 2
        return when (focus) {
            Focus.STRENGTH    -> strength(goal, exp, week, isBeginner)
            Focus.HYPERTROPHY -> hypertrophy(goal, exp, week, isBeginner)
            Focus.GENERAL     -> general(goal, isBeginner)
            Focus.FAT_LOSS    -> fatLoss(goal)
        }
    }

    private fun strength(goal: Goal, exp: Int, week: Int, beginner: Boolean): SplitParams {
        // Williams 2017: undulating periodization waves
        val (repMin, repMax) = if (beginner) 5 to 8 else when (week % 4) {
            0    -> 4 to 6
            1    -> 3 to 5
            2    -> 2 to 4
            else -> 5 to 6  // deload lite
        }
        val goalVolumeFactor = goalVolumeFactor(goal)
        val primSets = if (beginner) 3 else ((exp + 1).coerceIn(3, 5) * goalVolumeFactor).toInt()
        return SplitParams(
            primarySets        = primSets,
            primaryRepMin      = repMin,
            primaryRepMax      = repMax,
            primaryRpe         = if (week % 4 == 3) 7.5f else 8.5f,
            primaryRestSeconds = 240,
            primarySlotCount   = if (beginner) 0 else 2,
            secondarySets      = (primSets - 1).coerceAtLeast(2),
            secondaryRepMin    = 5,
            secondaryRepMax    = 8,
            secondaryRpe       = 7.5f,
            secondaryRestSeconds = 150,
            isolationSets      = 2,
            isolationRepMin    = 8,
            isolationRepMax    = 12,
            isolationRpe       = 7.0f,
            isolationRestSeconds = 90,
            exercisesPerSession = 7
        )
    }

    private fun hypertrophy(goal: Goal, exp: Int, week: Int, beginner: Boolean): SplitParams {
        // Schoenfeld 2017: 8-15 reps, ≥10 sets/muscle/week. Singer 2024: 60-90s rest.
        val (primMin, primMax) = if (beginner) 10 to 15 else when (week % 3) {
            0    -> 8  to 12
            1    -> 10 to 15
            else -> 6  to 10
        }
        val goalVolumeFactor = goalVolumeFactor(goal)
        val baseSets = baseSets(exp)
        val primSets = (baseSets * goalVolumeFactor).toInt().coerceIn(2, 5)
        return SplitParams(
            primarySets          = primSets,
            primaryRepMin        = primMin,
            primaryRepMax        = primMax,
            primaryRpe           = if (beginner) 7.0f else 8.0f,
            primaryRestSeconds   = if (beginner) 90 else 150,
            primarySlotCount     = if (beginner) 0 else 2,
            secondarySets        = (primSets - 1).coerceAtLeast(2),
            secondaryRepMin      = 10,
            secondaryRepMax      = 15,
            secondaryRpe         = 7.5f,
            secondaryRestSeconds = 90,
            isolationSets        = 2,
            isolationRepMin      = 12,
            isolationRepMax      = 15,
            isolationRpe         = 7.0f,
            isolationRestSeconds = 60,
            exercisesPerSession  = 8
        )
    }

    private fun general(goal: Goal, beginner: Boolean): SplitParams {
        val factor = goalVolumeFactor(goal)
        val sets = (3 * factor).toInt().coerceIn(2, 4)
        return SplitParams(
            primarySets          = sets,
            primaryRepMin        = 8,
            primaryRepMax        = 12,
            primaryRpe           = 7.5f,
            primaryRestSeconds   = 120,
            primarySlotCount     = if (beginner) 0 else 1,
            secondarySets        = sets,
            secondaryRepMin      = 10,
            secondaryRepMax      = 15,
            secondaryRpe         = 7.0f,
            secondaryRestSeconds = 90,
            isolationSets        = 2,
            isolationRepMin      = 12,
            isolationRepMax      = 15,
            isolationRpe         = 7.0f,
            isolationRestSeconds = 60,
            exercisesPerSession  = 7
        )
    }

    private fun fatLoss(goal: Goal): SplitParams {
        val factor = goalVolumeFactor(goal)
        val sets = (3 * factor).toInt().coerceIn(2, 4)
        return SplitParams(
            primarySets          = sets,
            primaryRepMin        = 10,
            primaryRepMax        = 15,
            primaryRpe           = 7.5f,
            primaryRestSeconds   = 60,
            primarySlotCount     = 1,
            secondarySets        = sets,
            secondaryRepMin      = 12,
            secondaryRepMax      = 20,
            secondaryRpe         = 7.5f,
            secondaryRestSeconds = 45,
            isolationSets        = 2,
            isolationRepMin      = 15,
            isolationRepMax      = 20,
            isolationRpe         = 7.0f,
            isolationRestSeconds = 30,
            exercisesPerSession  = 8
        )
    }

    private fun deload(base: SplitParams): SplitParams = base.copy(
        primarySets          = (base.primarySets * 0.65f).toInt().coerceAtLeast(1),
        secondarySets        = (base.secondarySets * 0.65f).toInt().coerceAtLeast(1),
        isolationSets        = (base.isolationSets * 0.65f).toInt().coerceAtLeast(1),
        primaryRpe           = 6.5f,
        secondaryRpe         = 6.5f,
        isolationRpe         = 6.0f,
        exercisesPerSession  = (base.exercisesPerSession - 2).coerceAtLeast(4)
    )

    /** ACSM: beginners 2 sets, intermediate 3, advanced 4+. */
    private fun baseSets(exp: Int) = when {
        exp <= 2 -> 2
        exp == 3 -> 3
        else     -> 4
    }

    /** Adjust total volume by goal — deficit = less volume, surplus = more. */
    private fun goalVolumeFactor(goal: Goal) = when (goal) {
        Goal.GAIN_MUSCLE -> 1.10f
        Goal.LOSE_FAT    -> 0.85f
        Goal.MAINTAIN    -> 1.00f
    }

    private fun exercisesFromDuration(mins: Int, beginner: Boolean): Int {
        val base = when {
            mins <= 30 -> 4
            mins <= 45 -> 5
            mins <= 60 -> 7
            else       -> 8
        }
        return if (beginner) (base - 1).coerceAtLeast(4) else base
    }
}
