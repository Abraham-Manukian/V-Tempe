package com.vtempe.server.features.ai.data.service.split

import com.vtempe.server.features.ai.data.service.split.TrainingConstants as C

/**
 * Base SplitParams per training focus — no user modifiers applied here.
 * All multipliers (goal / age / lifestyle / sex) are applied by SplitParamsFactory.
 */
internal object FocusPresets {

    fun strength(exp: Int, week: Int, beginner: Boolean): SplitParams {
        // Williams 2017: daily undulating periodization
        val (repMin, repMax) = if (beginner) 5 to 8 else when (week % 4) {
            0    -> 4 to 6
            1    -> 3 to 5
            2    -> 2 to 4
            else -> 5 to 6
        }
        val primSets = if (beginner) 3 else (exp + 1).coerceIn(3, 5)
        val rpe = if (!beginner && week % 4 == 3) C.RPE_PRIMARY_LITE else C.RPE_PRIMARY
        return SplitParams(
            primarySets          = primSets,
            primaryRepMin        = repMin,
            primaryRepMax        = repMax,
            primaryRpe           = rpe,
            primaryRestSeconds   = C.REST_PRIMARY_STRENGTH,
            primarySlotCount     = if (beginner) 0 else 2,
            secondarySets        = (primSets - 1).coerceAtLeast(2),
            secondaryRepMin      = 5,
            secondaryRepMax      = 8,
            secondaryRpe         = C.RPE_SECONDARY,
            secondaryRestSeconds = C.REST_SECONDARY_STRENGTH,
            isolationSets        = 2,
            isolationRepMin      = 8,
            isolationRepMax      = 12,
            isolationRpe         = C.RPE_ISOLATION,
            isolationRestSeconds = C.REST_ISOLATION_STRENGTH,
            exercisesPerSession  = 7
        )
    }

    fun hypertrophy(exp: Int, week: Int, beginner: Boolean): SplitParams {
        // Schoenfeld 2017: 8–15 reps, ≥10 sets/muscle/week. Singer 2024: 60–90 s rest.
        val (primMin, primMax) = if (beginner) 10 to 15 else when (week % 3) {
            0    -> 8  to 12
            1    -> 10 to 15
            else -> 6  to 10
        }
        val primSets = baseSets(exp)
        return SplitParams(
            primarySets          = primSets,
            primaryRepMin        = primMin,
            primaryRepMax        = primMax,
            primaryRpe           = if (beginner) C.RPE_SECONDARY else C.RPE_PRIMARY,
            primaryRestSeconds   = if (beginner) C.REST_SECONDARY_HYPERTROPHY else C.REST_PRIMARY_HYPERTROPHY,
            primarySlotCount     = if (beginner) 0 else 2,
            secondarySets        = (primSets - 1).coerceAtLeast(2),
            secondaryRepMin      = 10,
            secondaryRepMax      = 15,
            secondaryRpe         = C.RPE_SECONDARY,
            secondaryRestSeconds = C.REST_SECONDARY_HYPERTROPHY,
            isolationSets        = 2,
            isolationRepMin      = 12,
            isolationRepMax      = 15,
            isolationRpe         = C.RPE_ISOLATION,
            isolationRestSeconds = C.REST_ISOLATION_DEFAULT,
            exercisesPerSession  = 8
        )
    }

    fun general(beginner: Boolean): SplitParams = SplitParams(
        primarySets          = 3,
        primaryRepMin        = 8,
        primaryRepMax        = 12,
        primaryRpe           = C.RPE_SECONDARY,
        primaryRestSeconds   = C.REST_PRIMARY_GENERAL,
        primarySlotCount     = if (beginner) 0 else 1,
        secondarySets        = 3,
        secondaryRepMin      = 10,
        secondaryRepMax      = 15,
        secondaryRpe         = C.RPE_ISOLATION,
        secondaryRestSeconds = C.REST_SECONDARY_GENERAL,
        isolationSets        = 2,
        isolationRepMin      = 12,
        isolationRepMax      = 15,
        isolationRpe         = C.RPE_ISOLATION,
        isolationRestSeconds = C.REST_ISOLATION_DEFAULT,
        exercisesPerSession  = 7
    )

    // Goal in a caloric deficit = preserve muscle mass, not mimic cardio.
    // Rep ranges match general training; rest is shorter than hypertrophy to keep sessions compact.
    fun fatLoss(): SplitParams = SplitParams(
        primarySets          = 3,
        primaryRepMin        = 8,
        primaryRepMax        = 12,
        primaryRpe           = C.RPE_SECONDARY,          // 7.5 — avoid failure in deficit
        primaryRestSeconds   = C.REST_PRIMARY_GENERAL,   // 120s
        primarySlotCount     = 1,
        secondarySets        = 3,
        secondaryRepMin      = 10,
        secondaryRepMax      = 15,
        secondaryRpe         = C.RPE_ISOLATION,          // 7.0
        secondaryRestSeconds = C.REST_SECONDARY_GENERAL, // 90s
        isolationSets        = 2,
        isolationRepMin      = 12,
        isolationRepMax      = 15,
        isolationRpe         = C.RPE_ISOLATION,
        isolationRestSeconds = C.REST_ISOLATION_DEFAULT, // 60s
        exercisesPerSession  = 7  // 1 fewer than hypertrophy — lower total volume in deficit
    )

    // ACSM: beginners 2 sets, intermediate 3, advanced 4+
    private fun baseSets(exp: Int) = when {
        exp <= C.BEGINNER_MAX_LEVEL -> 2
        exp == 3                    -> 3
        else                        -> 4
    }
}
