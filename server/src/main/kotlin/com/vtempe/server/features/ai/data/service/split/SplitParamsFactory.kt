package com.vtempe.server.features.ai.data.service.split

import com.vtempe.server.features.ai.data.service.split.TrainingConstants as C

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
        weekIndex: Int,
        forceDeload: Boolean = false,
        hasHistory: Boolean = true
    ): SplitParams {
        val isDeload   = forceDeload || (weekIndex > 0 && weekIndex % C.DELOAD_WEEK_MODULO == C.DELOAD_WEEK_INDEX)
        val isBeginner = experienceLevel <= C.BEGINNER_MAX_LEVEL
        val exercises  = exercisesFromDuration(sessionDurationMins, isBeginner)
        val factor     = goalFactor(goal) * ageFactor(age) * lifestyleFactor(lifestyle)
        val base       = buildBase(focus, experienceLevel, weekIndex, isBeginner, sex)
                             .copy(exercisesPerSession = exercises)
                             .applyVolumeFactor(factor, age)
        val withDeload = if (isDeload) deload(base) else base
        // First-session safety: with no logged history we cannot know the athlete's true
        // strength, so cap RPE conservatively to avoid prescribing near-failure loads blind.
        return if (!hasHistory) withDeload.applyFirstSessionRpeCap(experienceLevel) else withDeload
    }

    /**
     * Caps RPE for athletes with no workout history. The cap scales with experience:
     * beginner (≤2) → 6.5, intermediate (3) → 7.0, advanced (≥4) → 7.5. No tier may exceed it.
     */
    private fun SplitParams.applyFirstSessionRpeCap(experienceLevel: Int): SplitParams {
        val cap = when {
            experienceLevel <= C.BEGINNER_MAX_LEVEL -> 6.5f
            experienceLevel == 3                    -> 7.0f
            else                                     -> 7.5f
        }
        return copy(
            primaryRpe   = minOf(primaryRpe, cap),
            secondaryRpe = minOf(secondaryRpe, cap),
            isolationRpe = minOf(isolationRpe, cap)
        )
    }

    // ── Enum parsing ──────────────────────────────────────────────────────────

    fun focusFromRaw(raw: String): Focus =
        runCatching { Focus.valueOf(raw.uppercase()) }.getOrDefault(Focus.GENERAL)

    fun goalFromRaw(raw: String): Goal =
        runCatching { Goal.valueOf(raw.uppercase()) }.getOrDefault(Goal.MAINTAIN)

    fun sexFromRaw(raw: String): Sex =
        runCatching { Sex.valueOf(raw.uppercase()) }.getOrDefault(Sex.OTHER)

    fun lifestyleFromRaw(raw: String): Lifestyle =
        runCatching { Lifestyle.valueOf(raw.uppercase()) }.getOrDefault(Lifestyle.LIGHT)

    // ── Private ───────────────────────────────────────────────────────────────

    private fun buildBase(focus: Focus, exp: Int, week: Int, beginner: Boolean, sex: Sex): SplitParams {
        val base = when (focus) {
            Focus.STRENGTH    -> FocusPresets.strength(exp, week, beginner)
            Focus.HYPERTROPHY -> FocusPresets.hypertrophy(exp, week, beginner)
            Focus.GENERAL     -> FocusPresets.general(beginner)
            Focus.FAT_LOSS    -> FocusPresets.fatLoss()
        }
        return if (sex == Sex.FEMALE) base.applyFemaleReps() else base
    }

    private fun goalFactor(goal: Goal) = when (goal) {
        Goal.GAIN_MUSCLE -> C.FACTOR_GOAL_GAIN_MUSCLE
        Goal.LOSE_FAT    -> C.FACTOR_GOAL_LOSE_FAT
        Goal.MAINTAIN    -> 1.0f
    }

    private fun ageFactor(age: Int) = when {
        age >= C.AGE_SENIOR  -> C.FACTOR_AGE_SENIOR
        age >= C.AGE_MASTERS -> C.FACTOR_AGE_MASTERS
        else                 -> 1.0f
    }

    private fun lifestyleFactor(lifestyle: Lifestyle) = when (lifestyle) {
        Lifestyle.VERY_ACTIVE -> C.FACTOR_LIFESTYLE_VERY_ACTIVE
        Lifestyle.ACTIVE      -> C.FACTOR_LIFESTYLE_ACTIVE
        Lifestyle.LIGHT       -> 1.0f
        Lifestyle.SEDENTARY   -> C.FACTOR_LIFESTYLE_SEDENTARY
    }

    private fun SplitParams.applyVolumeFactor(factor: Float, age: Int): SplitParams = copy(
        primarySets      = (primarySets * factor).toInt().coerceAtLeast(1),
        secondarySets    = (secondarySets * factor).toInt().coerceAtLeast(1),
        isolationSets    = (isolationSets * factor).toInt().coerceAtLeast(1),
        primarySlotCount = (primarySlotCount - if (age >= C.AGE_SENIOR) 1 else 0).coerceAtLeast(0)
    )

    private fun SplitParams.applyFemaleReps(): SplitParams = copy(
        primaryRepMin   = primaryRepMin + C.FEMALE_REP_OFFSET_COMPOUND,
        primaryRepMax   = primaryRepMax + C.FEMALE_REP_OFFSET_COMPOUND,
        secondaryRepMin = secondaryRepMin + C.FEMALE_REP_OFFSET_COMPOUND,
        secondaryRepMax = secondaryRepMax + C.FEMALE_REP_OFFSET_COMPOUND,
        isolationRepMin = isolationRepMin + C.FEMALE_REP_OFFSET_ISOLATION,
        isolationRepMax = isolationRepMax + C.FEMALE_REP_OFFSET_ISOLATION
    )

    private fun deload(base: SplitParams): SplitParams = base.copy(
        primarySets         = (base.primarySets * C.DELOAD_VOLUME_FACTOR).toInt().coerceAtLeast(1),
        secondarySets       = (base.secondarySets * C.DELOAD_VOLUME_FACTOR).toInt().coerceAtLeast(1),
        isolationSets       = (base.isolationSets * C.DELOAD_VOLUME_FACTOR).toInt().coerceAtLeast(1),
        primaryRpe          = C.RPE_DELOAD_PRIMARY,
        secondaryRpe        = C.RPE_DELOAD_SECONDARY,
        isolationRpe        = C.RPE_DELOAD_ISOLATION,
        exercisesPerSession = (base.exercisesPerSession - C.DELOAD_EXERCISE_REDUCTION).coerceAtLeast(C.DELOAD_MIN_EXERCISES)
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
