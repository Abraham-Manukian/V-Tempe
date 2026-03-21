package com.vtempe.server.features.ai.domain.model

enum class MovementPattern(
    val id: String,
    val token: String,
    val promptDescription: String
) {
    KNEE_DOMINANT(
        id = "knee_dominant",
        token = "pattern:knee_dominant",
        promptDescription = "squat or quad-dominant lower-body work"
    ),
    HINGE(
        id = "hinge",
        token = "pattern:hinge",
        promptDescription = "hip hinge or posterior-chain work"
    ),
    HORIZONTAL_PUSH(
        id = "horizontal_push",
        token = "pattern:horizontal_push",
        promptDescription = "horizontal press for chest, shoulders, and triceps"
    ),
    HORIZONTAL_PULL(
        id = "horizontal_pull",
        token = "pattern:horizontal_pull",
        promptDescription = "horizontal pull or rowing work"
    ),
    VERTICAL_PUSH(
        id = "vertical_push",
        token = "pattern:vertical_push",
        promptDescription = "vertical press overhead"
    ),
    VERTICAL_PULL(
        id = "vertical_pull",
        token = "pattern:vertical_pull",
        promptDescription = "vertical pull like pull-ups or lat-focused work"
    ),
    SINGLE_LEG(
        id = "single_leg",
        token = "pattern:single_leg",
        promptDescription = "single-leg lower-body work"
    ),
    CORE(
        id = "core",
        token = "pattern:core",
        promptDescription = "core stability or trunk control"
    ),
    ARM_FLEXION(
        id = "arm_flexion",
        token = "pattern:arm_flexion",
        promptDescription = "biceps or elbow-flexion accessory work"
    ),
    ARM_EXTENSION(
        id = "arm_extension",
        token = "pattern:arm_extension",
        promptDescription = "triceps or elbow-extension accessory work"
    ),
    CONDITIONING(
        id = "conditioning",
        token = "pattern:conditioning",
        promptDescription = "cardio or conditioning work"
    ),
    MOBILITY(
        id = "mobility",
        token = "pattern:mobility",
        promptDescription = "mobility, yoga, or recovery work"
    );

    companion object {
        private val aliases = mapOf(
            "legs" to KNEE_DOMINANT,
            "pattern:legs" to KNEE_DOMINANT,
            "lower_body" to KNEE_DOMINANT,
            "posterior_chain" to HINGE,
            "pattern:posterior_chain" to HINGE,
            "push" to HORIZONTAL_PUSH,
            "horizontal_press" to HORIZONTAL_PUSH,
            "press" to HORIZONTAL_PUSH,
            "pull" to HORIZONTAL_PULL,
            "row" to HORIZONTAL_PULL,
            "overhead_press" to VERTICAL_PUSH,
            "press_overhead" to VERTICAL_PUSH,
            "pull_up_pattern" to VERTICAL_PULL,
            "singleleg" to SINGLE_LEG,
            "single_leg_lower" to SINGLE_LEG,
            "abs" to CORE,
            "biceps" to ARM_FLEXION,
            "triceps" to ARM_EXTENSION,
            "cardio" to CONDITIONING,
            "recovery" to MOBILITY,
            "yoga" to MOBILITY
        )

        fun fromToken(raw: String): MovementPattern? {
            val normalized = raw
                .trim()
                .lowercase()
                .replace(' ', '_')
                .replace('-', '_')
                .removePrefix("pattern:")
                .takeIf { it.isNotEmpty() }
                ?: return null

            return entries.firstOrNull { it.id == normalized } ?: aliases[normalized]
        }
    }
}
