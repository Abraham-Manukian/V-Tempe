package com.vtempe.shared.domain.training

/** Movement patterns of the exercise catalog. Names match the server's `MovementPattern`. */
enum class MovementPattern {
    KNEE_DOMINANT, SINGLE_LEG, HINGE, HORIZONTAL_PUSH, HORIZONTAL_PULL, VERTICAL_PUSH, VERTICAL_PULL,
    ARM_FLEXION, ARM_EXTENSION, CORE, MOBILITY, CONDITIONING
}

/**
 * Exercises a user must not be given because of injuries — client port of the server's
 * `ExerciseRestrictions` (InjuryFilter patterns + InjuryRestrictions ids). An exercise is banned
 * when its id or ANY of its movement patterns is. Pinned to the server by
 * `testdata/injury-restriction-cases.tsv` and, keyword by keyword, `testdata/injury-keyword-rules.tsv`.
 */
class ExerciseRestrictions(
    val bannedPatterns: Set<MovementPattern>,
    val bannedIds: Set<String>,
) {
    fun allows(id: String, primary: MovementPattern, secondary: Set<MovementPattern>): Boolean =
        id !in bannedIds && primary !in bannedPatterns && secondary.none { it in bannedPatterns }

    companion object {
        val None = ExerciseRestrictions(emptySet(), emptySet())

        fun forInjuries(injuries: List<String>): ExerciseRestrictions {
            if (injuries.isEmpty()) return None
            val patterns = mutableSetOf<MovementPattern>()
            val ids = mutableSetOf<String>()
            injuries.map { it.lowercase() }.forEach { injury ->
                PatternRules.forEach { (keywords, banned) -> if (keywords.any { injury.contains(it) }) patterns += banned }
                IdRules.forEach { (keywords, banned) -> if (keywords.any { injury.contains(it) }) ids += banned }
            }
            return ExerciseRestrictions(patterns, ids)
        }

        /** Every keyword the rules react to; pinned to the server, keyword by keyword, by
         *  testdata/injury-keyword-rules.tsv. */
        internal val keywords: Set<String>
            get() = (PatternRules + IdRules.map { (keywords, _) -> keywords to emptySet<MovementPattern>() })
                .flatMapTo(mutableSetOf()) { it.first }

        /** Body-part keywords → banned patterns (server InjuryFilter.bannedPatterns). */
        private val PatternRules: List<Pair<Set<String>, Set<MovementPattern>>> = listOf(
            setOf("knee", "колено", "колен", "knees", "kneecap", "patella", "mcl", "acl", "pcl", "meniscus", "patellofemoral") to
                setOf(MovementPattern.KNEE_DOMINANT, MovementPattern.SINGLE_LEG),
            setOf("shoulder", "плечо", "плеч", "rotator", "cuff", "labrum", "ac joint", "impingement", "тендинопатия", "tendinopathy") to
                setOf(MovementPattern.VERTICAL_PUSH),
            setOf("back", "spine", "lumbar", "поясниц", "спин", "disc", "disk", "herniat", "грыж", "l4", "l5", "s1", "scoliosis", "сколиоз") to
                setOf(MovementPattern.HINGE),
            setOf("elbow", "локоть", "локт", "tennis elbow", "golfer", "epicondyl") to
                setOf(MovementPattern.ARM_FLEXION, MovementPattern.ARM_EXTENSION),
            setOf("wrist", "запястье", "запястн", "carpal") to
                setOf(MovementPattern.HORIZONTAL_PUSH, MovementPattern.ARM_FLEXION, MovementPattern.ARM_EXTENSION),
            setOf("neck", "шея", "шей", "cervical", "цервикальн") to setOf(MovementPattern.VERTICAL_PUSH),
            setOf("ankle", "лодыжка", "лодыж", "голеностоп", "achilles", "ахилл") to setOf(MovementPattern.SINGLE_LEG),
        )

        /** Keywords → banned exercise ids (server InjuryFilter.bannedExerciseIds + InjuryRestrictions). */
        private val IdRules: List<Pair<Set<String>, Set<String>>> = listOf(
            setOf("брусья", "брусьях", "dip bar", "dip bars", "parallel bar", "parallel bars") to setOf("dip", "bench_dip"),
            setOf("широким хватом", "wide grip", "wide-grip", "wide pullup", "wide pull up", "wide pull-up") to setOf("wide_pullup"),
            setOf("knee", "колен", "мениск", "meniscus", "patell", "пателл", "ligament", "acl", "pcl", "mcl") to setOf(
                "squat", "goblet_squat", "front_squat", "sumo_squat", "box_squat", "hack_squat", "leg_extension", "jump_squat",
                "box_jump", "bulgarian_split_squat", "step_up", "pistol_squat", "lunge", "reverse_lunge", "lateral_lunge",
                "skater_lunge", "burpee", "jump_rope", "skater_jump", "high_knees"
            ),
            setOf("back", "спин", "поясниц", "lumbar", "disc", "диск", "hernia", "грыж", "sciatica", "ишиас") to setOf(
                "deadlift", "sumo_deadlift", "romanian_deadlift", "single_leg_deadlift", "good_morning", "kettlebell_swing",
                "t_bar_row", "row", "squat", "front_squat", "box_squat", "burpee", "russian_twist"
            ),
            setOf("shoulder", "плеч", "rotator", "ротатор", "cuff", "манжет", "impingement", "импинджмент") to setOf(
                "ohp", "dumbbell_shoulder_press", "arnold_press", "lateral_raise", "front_raise", "upright_row",
                "handstand_pushup", "muscle_up"
            ),
            setOf("wrist", "запяст", "кист", "carpal", "карпал") to setOf(
                "pushup", "diamond_pushup", "wide_pushup", "decline_pushup", "incline_pushup", "plank", "mountain_climber",
                "ab_wheel", "handstand_pushup", "bench", "incline_bench", "close_grip_bench", "curl", "reverse_curl", "skull_crusher"
            ),
            setOf("elbow", "локт", "epicondyl", "эпикондил", "tennis", "golfer") to setOf(
                "curl", "hammer_curl", "incline_curl", "concentration_curl", "cable_curl", "reverse_curl", "tricep_extension",
                "skull_crusher", "tricep_pushdown", "tricep_kickback", "close_grip_bench", "row", "dumbbell_row", "cable_row"
            ),
            setOf("hip", "бедр", "таз", "pelvi") to setOf(
                "hip_thrust", "glute_bridge", "single_leg_deadlift", "pistol_squat", "lateral_lunge", "skater_lunge",
                "lunge", "reverse_lunge", "bulgarian_split_squat"
            ),
            setOf("ankle", "голено", "голеностоп", "achilles", "ахилл") to setOf(
                "jump_squat", "box_jump", "jump_rope", "burpee", "skater_jump", "high_knees", "sprint", "run", "lunge",
                "step_up", "stair_climb"
            ),
            setOf("neck", "шея", "шейн", "cervical", "цервикал") to setOf(
                "squat", "front_squat", "ohp", "crunch", "bicycle_crunch", "cable_crunch", "toes_to_bar", "hanging_leg_raise"
            ),
        )
    }
}

/** No workout can be built within the user's injuries and equipment. */
class NoSafeTrainingPlanException(message: String) : IllegalStateException(message)
