package com.vtempe.server.features.ai.data.service.split

/**
 * Maps (trainingFocus, experienceLevel, sessionDurationMins, weekIndex) → SplitParams.
 *
 * Sources:
 *  ACSM 2024 — sets/reps/rest by goal and experience
 *  Schoenfeld 2017 — ≥10 sets/muscle/week for hypertrophy
 *  Singer 2024 — 60–90 s rest optimal for hypertrophy; >90 s for strength
 *  Williams 2017 — undulating periodization superior for strength
 */
internal object SplitParamsFactory {

    enum class Focus { STRENGTH, HYPERTROPHY, GENERAL, FAT_LOSS }

    fun create(
        focus: Focus,
        experienceLevel: Int,
        sessionDurationMins: Int,
        weekIndex: Int
    ): SplitParams {
        val sets = baseSets(experienceLevel)
        val exercises = exercisesFromDuration(sessionDurationMins)
        return when (focus) {
            Focus.STRENGTH    -> strength(sets, exercises, weekIndex)
            Focus.HYPERTROPHY -> hypertrophy(sets, exercises, weekIndex)
            Focus.GENERAL     -> general(sets, exercises)
            Focus.FAT_LOSS    -> fatLoss(exercises)
        }
    }

    fun focusFromRaw(raw: String): Focus =
        runCatching { Focus.valueOf(raw.uppercase()) }.getOrDefault(Focus.GENERAL)

    // ── Private ──────────────────────────────────────────────────────────────

    /** ACSM: beginners 2 sets, intermediate 3, advanced 4. */
    private fun baseSets(experience: Int) = when {
        experience <= 2 -> 2
        experience == 3 -> 3
        else            -> 4
    }

    /** Exercises per session derived from available time, leaving room for warm-up and rest. */
    private fun exercisesFromDuration(mins: Int) = when {
        mins <= 30 -> 3
        mins <= 45 -> 4
        mins <= 60 -> 5
        else       -> 6
    }

    // ACSM: 80–100% 1RM, long rest. Williams 2017: undulating waves improve strength.
    private fun strength(sets: Int, exercises: Int, week: Int): SplitParams {
        val (repMin, repMax) = when (week % 4) {
            0    -> 4 to 6   // accumulation
            1    -> 3 to 5   // intensification
            2    -> 2 to 4   // peak
            else -> 5 to 6   // deload
        }
        return SplitParams(
            setsCompound         = (sets + 1).coerceAtMost(5),
            setsIsolation        = 2,
            compoundRepMin       = repMin,
            compoundRepMax       = repMax,
            isolationRepMin      = 6,
            isolationRepMax      = 10,
            rpeCompound          = if (week % 4 == 3) 7.0f else 8.5f,
            rpeIsolation         = 7.0f,
            restCompoundSeconds  = 240,
            restIsolationSeconds = 90,
            exercisesPerSession  = (exercises - 1).coerceAtLeast(2)
        )
    }

    // Schoenfeld 2017: 8–15 reps, ≥10 sets/muscle/week. Singer 2024: 60–90 s rest.
    private fun hypertrophy(sets: Int, exercises: Int, week: Int): SplitParams {
        val (repMin, repMax) = when (week % 3) {
            0    -> 10 to 15
            1    -> 8  to 12
            else -> 12 to 15
        }
        return SplitParams(
            setsCompound         = sets,
            setsIsolation        = sets,
            compoundRepMin       = repMin,
            compoundRepMax       = repMax,
            isolationRepMin      = 12,
            isolationRepMax      = 15,
            rpeCompound          = 7.5f,
            rpeIsolation         = 7.5f,
            restCompoundSeconds  = 90,
            restIsolationSeconds = 60,
            exercisesPerSession  = exercises
        )
    }

    private fun general(sets: Int, exercises: Int) = SplitParams(
        setsCompound         = sets.coerceAtMost(3),
        setsIsolation        = 2,
        compoundRepMin       = 10,
        compoundRepMax       = 15,
        isolationRepMin      = 12,
        isolationRepMax      = 15,
        rpeCompound          = 7.0f,
        rpeIsolation         = 7.0f,
        restCompoundSeconds  = 90,
        restIsolationSeconds = 60,
        exercisesPerSession  = exercises
    )

    private fun fatLoss(exercises: Int) = SplitParams(
        setsCompound         = 3,
        setsIsolation        = 2,
        compoundRepMin       = 12,
        compoundRepMax       = 20,
        isolationRepMin      = 15,
        isolationRepMax      = 20,
        rpeCompound          = 7.5f,
        rpeIsolation         = 7.5f,
        restCompoundSeconds  = 45,
        restIsolationSeconds = 30,
        exercisesPerSession  = exercises
    )
}
