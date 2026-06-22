package com.vtempe.server.features.ai.data.service.split

/** All numeric training constants in one place — change here, applies everywhere. */
internal object TrainingConstants {

    // ── PRIMARY tier ─────────────────────────────────────────────────────────
    const val RPE_PRIMARY            = 8.5f
    const val RPE_PRIMARY_LITE       = 7.5f   // strength deload-lite week (week % 4 == 3)
    const val REST_PRIMARY_STRENGTH  = 240    // 4 min — neural recovery for heavy compounds
    const val REST_PRIMARY_HYPERTROPHY = 150  // 2.5 min
    const val REST_PRIMARY_GENERAL   = 120

    // ── SECONDARY tier ───────────────────────────────────────────────────────
    const val RPE_SECONDARY              = 7.5f
    const val REST_SECONDARY_STRENGTH    = 150
    const val REST_SECONDARY_HYPERTROPHY = 90
    const val REST_SECONDARY_GENERAL     = 90
    const val REST_SECONDARY_FAT_LOSS    = 45

    // ── ISOLATION tier ───────────────────────────────────────────────────────
    const val RPE_ISOLATION              = 7.0f
    const val REST_ISOLATION_DEFAULT     = 60
    const val REST_ISOLATION_STRENGTH    = 90
    const val REST_ISOLATION_FAT_LOSS    = 30

    // ── Deload ───────────────────────────────────────────────────────────────
    // Primary trigger: 2+ consecutive weeks of poor performance (see shouldForceDeload).
    // Fallback timer: every DELOAD_WEEK_MODULO weeks regardless of performance.
    const val DELOAD_VOLUME_FACTOR       = 0.65f
    const val DELOAD_WEEK_MODULO         = 8     // fallback — fires only when RPE signal absent
    const val DELOAD_WEEK_INDEX          = 7
    const val RPE_DELOAD_PRIMARY         = 6.5f
    const val RPE_DELOAD_SECONDARY       = 6.5f
    const val RPE_DELOAD_ISOLATION       = 6.0f
    const val DELOAD_EXERCISE_REDUCTION  = 2
    const val DELOAD_MIN_EXERCISES       = 4
    const val DELOAD_RPE_TRIGGER         = 8.5   // averageRpe above this = signal to deload
    const val DELOAD_COMPLETION_TRIGGER  = 0.75  // completionRate below this = signal to deload
    const val DELOAD_SIGNAL_WEEKS        = 2     // consecutive weeks needed to trigger

    // ── Volume factors ────────────────────────────────────────────────────────
    const val FACTOR_GOAL_GAIN_MUSCLE      = 1.10f
    const val FACTOR_GOAL_LOSE_FAT         = 0.85f
    const val FACTOR_AGE_MASTERS           = 0.95f   // 50–59: mild -5%
    const val FACTOR_AGE_SENIOR            = 0.88f   // 60+: moderate -12%
    const val FACTOR_LIFESTYLE_SEDENTARY   = 1.05f   // more gym recovery capacity → slight +5%
    const val FACTOR_LIFESTYLE_ACTIVE      = 0.95f
    const val FACTOR_LIFESTYLE_VERY_ACTIVE = 0.90f   // high external load → -10% gym volume

    // ── Sex rep offset ────────────────────────────────────────────────────────
    // Small prior for fatigue resistance difference; overridden quickly by real user data.
    const val FEMALE_REP_OFFSET_COMPOUND  = 2
    const val FEMALE_REP_OFFSET_ISOLATION = 1

    // ── Age thresholds ────────────────────────────────────────────────────────
    const val AGE_MASTERS = 50   // ACSM Masters category starts at 50+
    const val AGE_SENIOR  = 60

    // ── Experience ───────────────────────────────────────────────────────────
    const val BEGINNER_MAX_LEVEL = 2
}
