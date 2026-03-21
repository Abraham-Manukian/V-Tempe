package com.vtempe.shared.domain.repository

enum class CoachActionType(val wireValue: String) {
    SHOW_CURRENT_WORKOUT("show_current_workout"),
    REPLACE_EXERCISE("replace_exercise"),
    REBUILD_TRAINING_PLAN("rebuild_training_plan"),
    SWITCH_TRAINING_MODE("switch_training_mode"),
    REBUILD_NUTRITION_PLAN("rebuild_nutrition_plan"),
    REFRESH_SLEEP_ADVICE("refresh_sleep_advice");

    companion object {
        fun fromWire(raw: String?): CoachActionType? {
            val normalized = raw
                ?.trim()
                ?.lowercase()
                ?.replace(' ', '_')
                ?.replace('-', '_')
                ?.takeIf { it.isNotEmpty() }
                ?: return null

            return entries.firstOrNull { it.wireValue == normalized }
        }
    }
}
