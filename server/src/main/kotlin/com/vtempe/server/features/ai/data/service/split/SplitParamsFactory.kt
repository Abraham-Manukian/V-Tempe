package com.vtempe.server.features.ai.data.service.split

/**
 * Maps user profile → SplitParams with three loading tiers per session (PDF + ACSM).
 *
 * PRIMARY   = 4×6-8,  RPE 8.5, 3 min rest  — main compound accents
 * SECONDARY = 3×8-12, RPE 7.5, 90s rest     — supporting compounds
 * ISOLATION = 2×10-15, RPE 7.0, 60s rest    — accessories
 *
 * Beginners (exp 1-2): no PRIMARY tier — only SECONDARY+ISOLATION.
 * Deload every 7th week: -35% volume, RPE capped at 7.
 * Age 36-50: -10% sets. Age 50+: -20% sets, -1 PRIMARY slot.
 * Female: +4 reps across all ranges (higher rep tolerance, Schoenfeld 2020).
 * Lifestyle VERY_ACTIVE: -20% volume. SEDENTARY: +10%.
 */
internal object SplitParamsFactory {

    enum class Focus     { STRENGTH, HYPERTROPHY, GENERAL, FAT_LOSS }
    enum class Goal      { GAIN_MUSCLE, LOSE_FAT, MAINTAIN }
    enum class Sex       { MALE, FEMALE, OTHER }
    enum class Lifestyle { SEDENTARY, LIGHT, ACTIVE, VERY_ACTIVE }

    fun create(
        goal: Goal,
        focus: Focus,
        experienceLevel: Int,
        age: Int,
        sex: Sex,
        lifestyle: Lifestyle,
        sessionDurationMins: Int,
        weekIndex: Int
    ): SplitParams {
        val isDeload   = weekIndex > 0 && weekIndex % 7 == 6
        val isBeginner = experienceLevel <= 2
        val exercises  = exercisesFromDuration(sessionDurationMins, isBeginner)
        val combined   = goalVolumeFactor(goal) * ageFactor(age) * lifestyleFactor(lifestyle)
        val base       = baseParams(goal, focus, experienceLevel, weekIndex, sex)
                             .copy(exercisesPerSession = exercises)
                             .applyVolumeFactor(combined, age)
        return if (isDeload) deload(base) else base
    }

    fun focusFromRaw(raw: String): Focus =
        runCatching { Focus.valueOf(raw.uppercase()) }.getOrDefault(Focus.GENERAL)

    fun goalFromRaw(raw: String): Goal =
        runCatching { Goal.valueOf(raw.uppercase()) }.getOrDefault(Goal.MAINTAIN)

    fun sexFromRaw(raw: String): Sex =
        runCatching { Sex.valueOf(raw.uppercase()) }.getOrDefault(Sex.OTHER)

    fun lifestyleFromRaw(raw: String): Lifestyle =
        runCatching { Lifestyle.valueOf(raw.uppercase()) }.getOrDefault(Lifestyle.LIGHT)

    // ── Private ──────────────────────────────────────────────────────────────

    private fun baseParams(goal: Goal, focus: Focus, exp: Int, week: Int, sex: Sex): SplitParams {
        val isBeginner = exp <= 2
        val base = when (focus) {
            Focus.STRENGTH    -> strength(exp, week, isBeginner)
            Focus.HYPERTROPHY -> hypertrophy(exp, week, isBeginner)
            Focus.GENERAL     -> general(isBeginner)
            Focus.FAT_LOSS    -> fatLoss()
        }
        return if (sex == Sex.FEMALE) base.applyFemaleReps() else base
    }

    private fun strength(exp: Int, week: Int, beginner: Boolean): SplitParams {
        // Williams 2017: undulating periodization waves
        val (repMin, repMax) = if (beginner) 5 to 8 else when (week % 4) {
            0    -> 4 to 6
            1    -> 3 to 5
            2    -> 2 to 4
            else -> 5 to 6
        }
        val primSets = if (beginner) 3 else (exp + 1).coerceIn(3, 5)
        return SplitParams(
            primarySets          = primSets,
            primaryRepMin        = repMin,
            primaryRepMax        = repMax,
            primaryRpe           = if (week % 4 == 3) 7.5f else 8.5f,
            primaryRestSeconds   = 240,
            primarySlotCount     = if (beginner) 0 else 2,
            secondarySets        = (primSets - 1).coerceAtLeast(2),
            secondaryRepMin      = 5,
            secondaryRepMax      = 8,
            secondaryRpe         = 7.5f,
            secondaryRestSeconds = 150,
            isolationSets        = 2,
            isolationRepMin      = 8,
            isolationRepMax      = 12,
            isolationRpe         = 7.0f,
            isolationRestSeconds = 90,
            exercisesPerSession  = 7
        )
    }

    private fun hypertrophy(exp: Int, week: Int, beginner: Boolean): SplitParams {
        // Schoenfeld 2017: 8-15 reps, ≥10 sets/muscle/week. Singer 2024: 60-90s rest.
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

    private fun general(beginner: Boolean): SplitParams = SplitParams(
        primarySets          = 3,
        primaryRepMin        = 8,
        primaryRepMax        = 12,
        primaryRpe           = 7.5f,
        primaryRestSeconds   = 120,
        primarySlotCount     = if (beginner) 0 else 1,
        secondarySets        = 3,
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

    private fun fatLoss(): SplitParams = SplitParams(
        primarySets          = 3,
        primaryRepMin        = 10,
        primaryRepMax        = 15,
        primaryRpe           = 7.5f,
        primaryRestSeconds   = 60,
        primarySlotCount     = 1,
        secondarySets        = 3,
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

    /** Older athletes recover slower — reduce total sets and cap PRIMARY slots. */
    private fun ageFactor(age: Int) = when {
        age >= 50 -> 0.80f
        age >= 36 -> 0.90f
        else      -> 1.00f
    }

    /**
     * VERY_ACTIVE workers accumulate fatigue before training — less gym volume needed.
     * SEDENTARY workers have more recovery capacity — can handle slightly more volume.
     */
    private fun lifestyleFactor(lifestyle: Lifestyle) = when (lifestyle) {
        Lifestyle.VERY_ACTIVE -> 0.80f
        Lifestyle.ACTIVE      -> 0.90f
        Lifestyle.LIGHT       -> 1.00f
        Lifestyle.SEDENTARY   -> 1.10f
    }

    /** Apply a combined volume multiplier to all set counts. 50+ also loses a PRIMARY slot. */
    private fun SplitParams.applyVolumeFactor(factor: Float, age: Int): SplitParams {
        val primaryPenalty = if (age >= 50) 1 else 0
        return copy(
            primarySets      = (primarySets * factor).toInt().coerceAtLeast(1),
            secondarySets    = (secondarySets * factor).toInt().coerceAtLeast(1),
            isolationSets    = (isolationSets * factor).toInt().coerceAtLeast(1),
            primarySlotCount = (primarySlotCount - primaryPenalty).coerceAtLeast(0)
        )
    }

    /**
     * Women have higher rep-to-failure tolerance (Schoenfeld 2020) — shift all rep
     * ranges up by 4 reps while keeping the same total sets and rest.
     */
    private fun SplitParams.applyFemaleReps(): SplitParams = copy(
        primaryRepMin      = primaryRepMin + 4,
        primaryRepMax      = primaryRepMax + 4,
        secondaryRepMin    = secondaryRepMin + 4,
        secondaryRepMax    = secondaryRepMax + 4,
        isolationRepMin    = isolationRepMin + 2,
        isolationRepMax    = isolationRepMax + 2
    )

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
