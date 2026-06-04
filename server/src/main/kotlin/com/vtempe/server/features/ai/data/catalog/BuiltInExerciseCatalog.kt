package com.vtempe.server.features.ai.data.catalog

import com.vtempe.server.features.ai.domain.model.ExerciseCatalogItem
import com.vtempe.server.features.ai.domain.model.MovementPattern
import com.vtempe.server.features.ai.domain.model.TrainingMode
import com.vtempe.server.features.ai.domain.port.ExerciseCatalog

/**
 * Master exercise catalog — 90+ exercises covering:
 *  • GYM (barbell, machines, cables, dumbbells)
 *  • HOME (bodyweight + dumbbells / bands / kettlebell)
 *  • OUTDOOR / PULLUP BAR (турник, park)
 *  • CARDIO for fat loss / conditioning
 *  • Mobility & recovery
 *
 * requiredEquipment is only set for HOME/OUTDOOR exercises that NEED the gear;
 * GYM mode bypasses the equipment check so machines etc. are always available.
 *
 * Priority: lower = preferred candidate when the resolver picks for a pattern.
 *  10 = primary compound   20 = strong alternative
 *  30 = good variation     40 = accessory / advanced
 *  50 = isolation / machine
 */
class BuiltInExerciseCatalog : ExerciseCatalog {

    private val items = listOf(

        // ── KNEE DOMINANT — squat / quad ────────────────────────────────────
        ExerciseCatalogItem(
            id = "squat",
            aliases = setOf("back_squat", "barbell_squat"),
            primaryPattern = MovementPattern.KNEE_DOMINANT,
            supportedModes = setOf(TrainingMode.GYM, TrainingMode.MIXED),
            requiredEquipment = setOf("barbell", "rack"),
            priority = 10
        ),
        ExerciseCatalogItem(
            id = "goblet_squat",
            aliases = setOf("goblet"),
            primaryPattern = MovementPattern.KNEE_DOMINANT,
            supportedModes = setOf(TrainingMode.GYM, TrainingMode.HOME, TrainingMode.MIXED),
            requiredEquipment = setOf("dumbbells", "kettlebell"),
            priority = 15
        ),
        ExerciseCatalogItem(
            id = "front_squat",
            aliases = setOf("frontsquat"),
            primaryPattern = MovementPattern.KNEE_DOMINANT,
            supportedModes = setOf(TrainingMode.GYM, TrainingMode.MIXED),
            requiredEquipment = setOf("barbell"),
            priority = 20
        ),
        ExerciseCatalogItem(
            id = "leg_press",
            aliases = setOf("legpress"),
            primaryPattern = MovementPattern.KNEE_DOMINANT,
            supportedModes = setOf(TrainingMode.GYM, TrainingMode.MIXED),
            priority = 25
        ),
        ExerciseCatalogItem(
            id = "sumo_squat",
            aliases = setOf("plie_squat", "wide_squat"),
            primaryPattern = MovementPattern.KNEE_DOMINANT,
            secondaryPatterns = setOf(MovementPattern.HINGE),
            supportedModes = setOf(TrainingMode.GYM, TrainingMode.HOME, TrainingMode.OUTDOOR, TrainingMode.MIXED),
            priority = 30
        ),
        ExerciseCatalogItem(
            id = "wall_sit",
            aliases = setOf("wall_squat"),
            primaryPattern = MovementPattern.KNEE_DOMINANT,
            supportedModes = setOf(TrainingMode.HOME, TrainingMode.OUTDOOR, TrainingMode.GYM, TrainingMode.MIXED),
            priority = 35
        ),
        ExerciseCatalogItem(
            id = "box_squat",
            primaryPattern = MovementPattern.KNEE_DOMINANT,
            supportedModes = setOf(TrainingMode.GYM, TrainingMode.MIXED),
            requiredEquipment = setOf("barbell", "rack"),
            priority = 30
        ),
        ExerciseCatalogItem(
            id = "leg_extension",
            aliases = setOf("quad_extension"),
            primaryPattern = MovementPattern.KNEE_DOMINANT,
            supportedModes = setOf(TrainingMode.GYM, TrainingMode.MIXED),
            priority = 50
        ),
        ExerciseCatalogItem(
            id = "jump_squat",
            aliases = setOf("squat_jump"),
            primaryPattern = MovementPattern.KNEE_DOMINANT,
            secondaryPatterns = setOf(MovementPattern.CONDITIONING),
            supportedModes = setOf(TrainingMode.HOME, TrainingMode.OUTDOOR, TrainingMode.GYM, TrainingMode.MIXED),
            priority = 40
        ),
        ExerciseCatalogItem(
            id = "hack_squat",
            aliases = setOf("machine_hack_squat"),
            primaryPattern = MovementPattern.KNEE_DOMINANT,
            supportedModes = setOf(TrainingMode.GYM, TrainingMode.MIXED),
            priority = 40
        ),
        ExerciseCatalogItem(
            id = "barbell_hack_squat",
            aliases = setOf("hack_lift", "behind_leg_squat"),
            primaryPattern = MovementPattern.KNEE_DOMINANT,
            secondaryPatterns = setOf(MovementPattern.HINGE),
            supportedModes = setOf(TrainingMode.GYM, TrainingMode.MIXED),
            requiredEquipment = setOf("barbell"),
            priority = 45
        ),

        // ── HINGE — hip hinge / posterior chain ─────────────────────────────
        ExerciseCatalogItem(
            id = "deadlift",
            aliases = setOf("conventional_deadlift"),
            primaryPattern = MovementPattern.HINGE,
            supportedModes = setOf(TrainingMode.GYM, TrainingMode.MIXED),
            requiredEquipment = setOf("barbell"),
            priority = 10
        ),
        ExerciseCatalogItem(
            id = "romanian_deadlift",
            aliases = setOf("rdl", "stiff_leg_deadlift"),
            primaryPattern = MovementPattern.HINGE,
            supportedModes = setOf(TrainingMode.GYM, TrainingMode.HOME, TrainingMode.MIXED),
            requiredEquipment = setOf("barbell", "dumbbells"),
            priority = 15
        ),
        ExerciseCatalogItem(
            id = "hip_thrust",
            aliases = setOf("hipthrust", "hip_thrusts", "barbell_hip_thrust"),
            primaryPattern = MovementPattern.HINGE,
            supportedModes = setOf(TrainingMode.GYM, TrainingMode.HOME, TrainingMode.MIXED),
            priority = 20
        ),
        ExerciseCatalogItem(
            id = "glute_bridge",
            aliases = setOf("bridge"),
            primaryPattern = MovementPattern.HINGE,
            supportedModes = setOf(TrainingMode.HOME, TrainingMode.GYM, TrainingMode.OUTDOOR, TrainingMode.MIXED),
            priority = 25
        ),
        ExerciseCatalogItem(
            id = "kettlebell_swing",
            aliases = setOf("kb_swing", "swing"),
            primaryPattern = MovementPattern.HINGE,
            secondaryPatterns = setOf(MovementPattern.CONDITIONING),
            supportedModes = setOf(TrainingMode.HOME, TrainingMode.GYM, TrainingMode.OUTDOOR, TrainingMode.MIXED),
            requiredEquipment = setOf("kettlebell", "dumbbells"),
            priority = 20
        ),
        ExerciseCatalogItem(
            id = "single_leg_deadlift",
            aliases = setOf("single_leg_rdl", "sl_deadlift"),
            primaryPattern = MovementPattern.HINGE,
            secondaryPatterns = setOf(MovementPattern.SINGLE_LEG),
            supportedModes = setOf(TrainingMode.HOME, TrainingMode.GYM, TrainingMode.OUTDOOR, TrainingMode.MIXED),
            priority = 30
        ),
        ExerciseCatalogItem(
            id = "sumo_deadlift",
            primaryPattern = MovementPattern.HINGE,
            supportedModes = setOf(TrainingMode.GYM, TrainingMode.MIXED),
            requiredEquipment = setOf("barbell"),
            priority = 30
        ),
        ExerciseCatalogItem(
            id = "good_morning",
            primaryPattern = MovementPattern.HINGE,
            supportedModes = setOf(TrainingMode.GYM, TrainingMode.MIXED),
            requiredEquipment = setOf("barbell"),
            priority = 40
        ),
        ExerciseCatalogItem(
            id = "nordic_curl",
            aliases = setOf("nordic_hamstring"),
            primaryPattern = MovementPattern.HINGE,
            supportedModes = setOf(TrainingMode.HOME, TrainingMode.GYM, TrainingMode.OUTDOOR, TrainingMode.MIXED),
            priority = 35
        ),
        ExerciseCatalogItem(
            id = "leg_curl",
            aliases = setOf("hamstring_curl"),
            primaryPattern = MovementPattern.HINGE,
            supportedModes = setOf(TrainingMode.GYM, TrainingMode.MIXED),
            priority = 50
        ),

        // ── SINGLE LEG — unilateral lower body ──────────────────────────────
        ExerciseCatalogItem(
            id = "lunge",
            aliases = setOf("walking_lunge", "forward_lunge"),
            primaryPattern = MovementPattern.SINGLE_LEG,
            secondaryPatterns = setOf(MovementPattern.KNEE_DOMINANT),
            supportedModes = setOf(TrainingMode.GYM, TrainingMode.HOME, TrainingMode.OUTDOOR, TrainingMode.MIXED),
            priority = 10
        ),
        ExerciseCatalogItem(
            id = "reverse_lunge",
            aliases = setOf("step_back_lunge"),
            primaryPattern = MovementPattern.SINGLE_LEG,
            supportedModes = setOf(TrainingMode.HOME, TrainingMode.GYM, TrainingMode.OUTDOOR, TrainingMode.MIXED),
            priority = 15
        ),
        ExerciseCatalogItem(
            id = "bulgarian_split_squat",
            aliases = setOf("split_squat", "rear_elevated_split_squat", "ress"),
            primaryPattern = MovementPattern.SINGLE_LEG,
            secondaryPatterns = setOf(MovementPattern.KNEE_DOMINANT),
            supportedModes = setOf(TrainingMode.HOME, TrainingMode.GYM, TrainingMode.MIXED),
            priority = 15
        ),
        ExerciseCatalogItem(
            id = "step_up",
            aliases = setOf("box_step_up"),
            primaryPattern = MovementPattern.SINGLE_LEG,
            secondaryPatterns = setOf(MovementPattern.KNEE_DOMINANT),
            supportedModes = setOf(TrainingMode.HOME, TrainingMode.GYM, TrainingMode.OUTDOOR, TrainingMode.MIXED),
            priority = 20
        ),
        ExerciseCatalogItem(
            id = "lateral_lunge",
            aliases = setOf("side_lunge"),
            primaryPattern = MovementPattern.SINGLE_LEG,
            supportedModes = setOf(TrainingMode.HOME, TrainingMode.GYM, TrainingMode.OUTDOOR, TrainingMode.MIXED),
            priority = 30
        ),
        ExerciseCatalogItem(
            id = "pistol_squat",
            aliases = setOf("single_leg_squat"),
            primaryPattern = MovementPattern.SINGLE_LEG,
            supportedModes = setOf(TrainingMode.HOME, TrainingMode.GYM, TrainingMode.OUTDOOR, TrainingMode.MIXED),
            priority = 35
        ),
        ExerciseCatalogItem(
            id = "skater_lunge",
            aliases = setOf("skater_squat", "curtsy_lunge"),
            primaryPattern = MovementPattern.SINGLE_LEG,
            supportedModes = setOf(TrainingMode.HOME, TrainingMode.GYM, TrainingMode.OUTDOOR, TrainingMode.MIXED),
            priority = 40
        ),

        // ── HORIZONTAL PUSH — chest / triceps ───────────────────────────────
        ExerciseCatalogItem(
            id = "bench",
            aliases = setOf("bench_press", "flat_bench"),
            primaryPattern = MovementPattern.HORIZONTAL_PUSH,
            supportedModes = setOf(TrainingMode.GYM, TrainingMode.MIXED),
            requiredEquipment = setOf("bench", "barbell", "dumbbells"),
            priority = 10
        ),
        ExerciseCatalogItem(
            id = "incline_bench",
            aliases = setOf("incline_press"),
            primaryPattern = MovementPattern.HORIZONTAL_PUSH,
            supportedModes = setOf(TrainingMode.GYM, TrainingMode.MIXED),
            requiredEquipment = setOf("bench", "barbell", "dumbbells"),
            priority = 20
        ),
        ExerciseCatalogItem(
            id = "pushup",
            aliases = setOf("push_up", "push_ups"),
            primaryPattern = MovementPattern.HORIZONTAL_PUSH,
            supportedModes = setOf(TrainingMode.GYM, TrainingMode.HOME, TrainingMode.OUTDOOR, TrainingMode.MIXED),
            priority = 20
        ),
        ExerciseCatalogItem(
            id = "dip",
            aliases = setOf("parallel_bar_dip", "chest_dip"),
            primaryPattern = MovementPattern.HORIZONTAL_PUSH,
            secondaryPatterns = setOf(MovementPattern.ARM_EXTENSION),
            supportedModes = setOf(TrainingMode.GYM, TrainingMode.HOME, TrainingMode.OUTDOOR, TrainingMode.MIXED),
            requiredEquipment = setOf("bench", "trx", "pullup_bar"),
            priority = 25
        ),
        ExerciseCatalogItem(
            id = "diamond_pushup",
            aliases = setOf("close_grip_pushup", "tricep_pushup"),
            primaryPattern = MovementPattern.HORIZONTAL_PUSH,
            secondaryPatterns = setOf(MovementPattern.ARM_EXTENSION),
            supportedModes = setOf(TrainingMode.HOME, TrainingMode.GYM, TrainingMode.OUTDOOR, TrainingMode.MIXED),
            priority = 30
        ),
        ExerciseCatalogItem(
            id = "wide_pushup",
            aliases = setOf("wide_grip_pushup"),
            primaryPattern = MovementPattern.HORIZONTAL_PUSH,
            supportedModes = setOf(TrainingMode.HOME, TrainingMode.GYM, TrainingMode.OUTDOOR, TrainingMode.MIXED),
            priority = 30
        ),
        ExerciseCatalogItem(
            id = "decline_pushup",
            primaryPattern = MovementPattern.HORIZONTAL_PUSH,
            supportedModes = setOf(TrainingMode.HOME, TrainingMode.OUTDOOR, TrainingMode.GYM, TrainingMode.MIXED),
            priority = 35
        ),
        ExerciseCatalogItem(
            id = "incline_pushup",
            aliases = setOf("elevated_pushup"),
            primaryPattern = MovementPattern.HORIZONTAL_PUSH,
            supportedModes = setOf(TrainingMode.HOME, TrainingMode.OUTDOOR, TrainingMode.GYM, TrainingMode.MIXED),
            priority = 40
        ),
        ExerciseCatalogItem(
            id = "dumbbell_fly",
            aliases = setOf("chest_fly", "db_fly"),
            primaryPattern = MovementPattern.HORIZONTAL_PUSH,
            supportedModes = setOf(TrainingMode.GYM, TrainingMode.HOME, TrainingMode.MIXED),
            requiredEquipment = setOf("dumbbells", "bench"),
            priority = 40
        ),
        ExerciseCatalogItem(
            id = "cable_fly",
            aliases = setOf("cable_chest_fly"),
            primaryPattern = MovementPattern.HORIZONTAL_PUSH,
            supportedModes = setOf(TrainingMode.GYM, TrainingMode.MIXED),
            priority = 45
        ),
        ExerciseCatalogItem(
            id = "pike_pushup",
            aliases = setOf("pike_push_up"),
            primaryPattern = MovementPattern.HORIZONTAL_PUSH,
            secondaryPatterns = setOf(MovementPattern.VERTICAL_PUSH),
            supportedModes = setOf(TrainingMode.HOME, TrainingMode.OUTDOOR, TrainingMode.GYM, TrainingMode.MIXED),
            priority = 35
        ),

        // ── HORIZONTAL PULL — back / rear deltoids ──────────────────────────
        ExerciseCatalogItem(
            id = "row",
            aliases = setOf("bent_over_row", "barbell_row"),
            primaryPattern = MovementPattern.HORIZONTAL_PULL,
            supportedModes = setOf(TrainingMode.GYM, TrainingMode.HOME, TrainingMode.MIXED),
            requiredEquipment = setOf("barbell", "dumbbells", "bands", "trx"),
            priority = 10
        ),
        ExerciseCatalogItem(
            id = "dumbbell_row",
            aliases = setOf("db_row", "one_arm_row", "single_arm_row"),
            primaryPattern = MovementPattern.HORIZONTAL_PULL,
            supportedModes = setOf(TrainingMode.GYM, TrainingMode.HOME, TrainingMode.MIXED),
            requiredEquipment = setOf("dumbbells"),
            priority = 15
        ),
        ExerciseCatalogItem(
            id = "inverted_row",
            aliases = setOf("bodyweight_row", "australian_pullup"),
            primaryPattern = MovementPattern.HORIZONTAL_PULL,
            supportedModes = setOf(TrainingMode.HOME, TrainingMode.GYM, TrainingMode.OUTDOOR, TrainingMode.MIXED),
            requiredEquipment = setOf("pullup_bar", "trx"),
            priority = 20
        ),
        ExerciseCatalogItem(
            id = "cable_row",
            aliases = setOf("seated_cable_row", "low_cable_row"),
            primaryPattern = MovementPattern.HORIZONTAL_PULL,
            supportedModes = setOf(TrainingMode.GYM, TrainingMode.MIXED),
            priority = 20
        ),
        ExerciseCatalogItem(
            id = "band_row",
            aliases = setOf("resistance_band_row"),
            primaryPattern = MovementPattern.HORIZONTAL_PULL,
            supportedModes = setOf(TrainingMode.HOME, TrainingMode.OUTDOOR, TrainingMode.MIXED),
            requiredEquipment = setOf("bands"),
            priority = 30
        ),
        ExerciseCatalogItem(
            id = "face_pull",
            aliases = setOf("cable_face_pull"),
            primaryPattern = MovementPattern.HORIZONTAL_PULL,
            supportedModes = setOf(TrainingMode.GYM, TrainingMode.HOME, TrainingMode.MIXED),
            requiredEquipment = setOf("bands"),
            priority = 35
        ),
        ExerciseCatalogItem(
            id = "chest_supported_row",
            aliases = setOf("incline_row"),
            primaryPattern = MovementPattern.HORIZONTAL_PULL,
            supportedModes = setOf(TrainingMode.GYM, TrainingMode.MIXED),
            requiredEquipment = setOf("bench", "dumbbells"),
            priority = 30
        ),
        ExerciseCatalogItem(
            id = "t_bar_row",
            aliases = setOf("tbar_row"),
            primaryPattern = MovementPattern.HORIZONTAL_PULL,
            supportedModes = setOf(TrainingMode.GYM, TrainingMode.MIXED),
            requiredEquipment = setOf("barbell"),
            priority = 30
        ),

        // ── VERTICAL PUSH — shoulders / overhead ────────────────────────────
        ExerciseCatalogItem(
            id = "ohp",
            aliases = setOf("overhead_press", "military_press", "standing_press"),
            primaryPattern = MovementPattern.VERTICAL_PUSH,
            supportedModes = setOf(TrainingMode.GYM, TrainingMode.HOME, TrainingMode.MIXED),
            requiredEquipment = setOf("barbell", "dumbbells", "bands", "kettlebell"),
            priority = 10
        ),
        ExerciseCatalogItem(
            id = "dumbbell_shoulder_press",
            aliases = setOf("db_shoulder_press", "seated_dumbbell_press"),
            primaryPattern = MovementPattern.VERTICAL_PUSH,
            supportedModes = setOf(TrainingMode.GYM, TrainingMode.HOME, TrainingMode.MIXED),
            requiredEquipment = setOf("dumbbells"),
            priority = 15
        ),
        ExerciseCatalogItem(
            id = "arnold_press",
            primaryPattern = MovementPattern.VERTICAL_PUSH,
            supportedModes = setOf(TrainingMode.GYM, TrainingMode.HOME, TrainingMode.MIXED),
            requiredEquipment = setOf("dumbbells"),
            priority = 25
        ),
        ExerciseCatalogItem(
            id = "lateral_raise",
            aliases = setOf("side_raise", "lateral_delt_raise"),
            primaryPattern = MovementPattern.VERTICAL_PUSH,
            supportedModes = setOf(TrainingMode.GYM, TrainingMode.HOME, TrainingMode.MIXED),
            requiredEquipment = setOf("dumbbells", "bands", "kettlebell"),
            priority = 30
        ),
        ExerciseCatalogItem(
            id = "front_raise",
            aliases = setOf("anterior_raise"),
            primaryPattern = MovementPattern.VERTICAL_PUSH,
            supportedModes = setOf(TrainingMode.GYM, TrainingMode.HOME, TrainingMode.MIXED),
            requiredEquipment = setOf("dumbbells", "bands"),
            priority = 40
        ),
        ExerciseCatalogItem(
            id = "handstand_pushup",
            aliases = setOf("hspu", "wall_handstand_pushup"),
            primaryPattern = MovementPattern.VERTICAL_PUSH,
            supportedModes = setOf(TrainingMode.HOME, TrainingMode.GYM, TrainingMode.OUTDOOR, TrainingMode.MIXED),
            priority = 35
        ),
        ExerciseCatalogItem(
            id = "upright_row",
            primaryPattern = MovementPattern.VERTICAL_PUSH,
            secondaryPatterns = setOf(MovementPattern.HORIZONTAL_PULL),
            supportedModes = setOf(TrainingMode.GYM, TrainingMode.HOME, TrainingMode.MIXED),
            requiredEquipment = setOf("barbell", "dumbbells", "bands"),
            priority = 40
        ),

        // ── VERTICAL PULL — lats / upper back ───────────────────────────────
        ExerciseCatalogItem(
            id = "pullup",
            aliases = setOf("pull_up", "pullups", "overhand_pullup"),
            primaryPattern = MovementPattern.VERTICAL_PULL,
            supportedModes = setOf(TrainingMode.GYM, TrainingMode.HOME, TrainingMode.OUTDOOR, TrainingMode.MIXED),
            requiredEquipment = setOf("pullup_bar", "trx"),
            priority = 10
        ),
        ExerciseCatalogItem(
            id = "chin_up",
            aliases = setOf("chinup", "supinated_pullup", "underhand_pullup"),
            primaryPattern = MovementPattern.VERTICAL_PULL,
            secondaryPatterns = setOf(MovementPattern.ARM_FLEXION),
            supportedModes = setOf(TrainingMode.GYM, TrainingMode.HOME, TrainingMode.OUTDOOR, TrainingMode.MIXED),
            requiredEquipment = setOf("pullup_bar"),
            priority = 15
        ),
        ExerciseCatalogItem(
            id = "wide_pullup",
            aliases = setOf("wide_grip_pullup"),
            primaryPattern = MovementPattern.VERTICAL_PULL,
            supportedModes = setOf(TrainingMode.GYM, TrainingMode.HOME, TrainingMode.OUTDOOR, TrainingMode.MIXED),
            requiredEquipment = setOf("pullup_bar"),
            priority = 20
        ),
        ExerciseCatalogItem(
            id = "lat_pulldown",
            aliases = setOf("cable_pulldown", "pulldown"),
            primaryPattern = MovementPattern.VERTICAL_PULL,
            supportedModes = setOf(TrainingMode.GYM, TrainingMode.MIXED),
            priority = 20
        ),
        ExerciseCatalogItem(
            id = "band_pulldown",
            aliases = setOf("resistance_band_pulldown"),
            primaryPattern = MovementPattern.VERTICAL_PULL,
            supportedModes = setOf(TrainingMode.HOME, TrainingMode.GYM, TrainingMode.MIXED),
            requiredEquipment = setOf("bands"),
            priority = 30
        ),
        ExerciseCatalogItem(
            id = "muscle_up",
            primaryPattern = MovementPattern.VERTICAL_PULL,
            secondaryPatterns = setOf(MovementPattern.HORIZONTAL_PUSH),
            supportedModes = setOf(TrainingMode.GYM, TrainingMode.OUTDOOR, TrainingMode.MIXED),
            requiredEquipment = setOf("pullup_bar"),
            priority = 35
        ),
        ExerciseCatalogItem(
            id = "assisted_pullup",
            aliases = setOf("band_assisted_pullup"),
            primaryPattern = MovementPattern.VERTICAL_PULL,
            supportedModes = setOf(TrainingMode.GYM, TrainingMode.HOME, TrainingMode.MIXED),
            requiredEquipment = setOf("bands"),
            priority = 30
        ),

        // ── CORE — abs / trunk stability ────────────────────────────────────
        ExerciseCatalogItem(
            id = "plank",
            aliases = setOf("plank_hold"),
            primaryPattern = MovementPattern.CORE,
            supportedModes = setOf(TrainingMode.GYM, TrainingMode.HOME, TrainingMode.OUTDOOR, TrainingMode.MIXED),
            priority = 10
        ),
        ExerciseCatalogItem(
            id = "side_plank",
            primaryPattern = MovementPattern.CORE,
            supportedModes = setOf(TrainingMode.HOME, TrainingMode.GYM, TrainingMode.OUTDOOR, TrainingMode.MIXED),
            priority = 20
        ),
        ExerciseCatalogItem(
            id = "crunch",
            aliases = setOf("ab_crunch"),
            primaryPattern = MovementPattern.CORE,
            supportedModes = setOf(TrainingMode.HOME, TrainingMode.GYM, TrainingMode.OUTDOOR, TrainingMode.MIXED),
            priority = 20
        ),
        ExerciseCatalogItem(
            id = "bicycle_crunch",
            primaryPattern = MovementPattern.CORE,
            supportedModes = setOf(TrainingMode.HOME, TrainingMode.GYM, TrainingMode.OUTDOOR, TrainingMode.MIXED),
            priority = 25
        ),
        ExerciseCatalogItem(
            id = "leg_raise",
            aliases = setOf("lying_leg_raise"),
            primaryPattern = MovementPattern.CORE,
            supportedModes = setOf(TrainingMode.HOME, TrainingMode.GYM, TrainingMode.OUTDOOR, TrainingMode.MIXED),
            priority = 25
        ),
        ExerciseCatalogItem(
            id = "hanging_leg_raise",
            aliases = setOf("hanging_knee_raise"),
            primaryPattern = MovementPattern.CORE,
            supportedModes = setOf(TrainingMode.HOME, TrainingMode.GYM, TrainingMode.OUTDOOR, TrainingMode.MIXED),
            requiredEquipment = setOf("pullup_bar"),
            priority = 20
        ),
        ExerciseCatalogItem(
            id = "toes_to_bar",
            aliases = setOf("toes2bar", "ttb"),
            primaryPattern = MovementPattern.CORE,
            secondaryPatterns = setOf(MovementPattern.VERTICAL_PULL),
            supportedModes = setOf(TrainingMode.HOME, TrainingMode.GYM, TrainingMode.OUTDOOR, TrainingMode.MIXED),
            requiredEquipment = setOf("pullup_bar"),
            priority = 25
        ),
        ExerciseCatalogItem(
            id = "mountain_climber",
            aliases = setOf("mountain_climbers"),
            primaryPattern = MovementPattern.CORE,
            secondaryPatterns = setOf(MovementPattern.CONDITIONING),
            supportedModes = setOf(TrainingMode.HOME, TrainingMode.GYM, TrainingMode.OUTDOOR, TrainingMode.MIXED),
            priority = 30
        ),
        ExerciseCatalogItem(
            id = "russian_twist",
            primaryPattern = MovementPattern.CORE,
            supportedModes = setOf(TrainingMode.HOME, TrainingMode.GYM, TrainingMode.OUTDOOR, TrainingMode.MIXED),
            priority = 30
        ),
        ExerciseCatalogItem(
            id = "dead_bug",
            primaryPattern = MovementPattern.CORE,
            supportedModes = setOf(TrainingMode.HOME, TrainingMode.GYM, TrainingMode.MIXED),
            priority = 25
        ),
        ExerciseCatalogItem(
            id = "hollow_body",
            aliases = setOf("hollow_hold"),
            primaryPattern = MovementPattern.CORE,
            supportedModes = setOf(TrainingMode.HOME, TrainingMode.GYM, TrainingMode.OUTDOOR, TrainingMode.MIXED),
            priority = 30
        ),
        ExerciseCatalogItem(
            id = "v_up",
            aliases = setOf("vup", "jackknife"),
            primaryPattern = MovementPattern.CORE,
            supportedModes = setOf(TrainingMode.HOME, TrainingMode.GYM, TrainingMode.OUTDOOR, TrainingMode.MIXED),
            priority = 35
        ),
        ExerciseCatalogItem(
            id = "cable_crunch",
            aliases = setOf("kneeling_cable_crunch"),
            primaryPattern = MovementPattern.CORE,
            supportedModes = setOf(TrainingMode.GYM, TrainingMode.MIXED),
            priority = 40
        ),
        ExerciseCatalogItem(
            id = "ab_wheel",
            aliases = setOf("rollout"),
            primaryPattern = MovementPattern.CORE,
            supportedModes = setOf(TrainingMode.HOME, TrainingMode.GYM, TrainingMode.MIXED),
            priority = 35
        ),
        ExerciseCatalogItem(
            id = "l_sit",
            primaryPattern = MovementPattern.CORE,
            secondaryPatterns = setOf(MovementPattern.ARM_EXTENSION),
            supportedModes = setOf(TrainingMode.HOME, TrainingMode.GYM, TrainingMode.OUTDOOR, TrainingMode.MIXED),
            requiredEquipment = setOf("pullup_bar"),
            priority = 40
        ),

        // ── ARM FLEXION — biceps ─────────────────────────────────────────────
        ExerciseCatalogItem(
            id = "curl",
            aliases = setOf("bicep_curl", "biceps_curl", "barbell_curl"),
            primaryPattern = MovementPattern.ARM_FLEXION,
            supportedModes = setOf(TrainingMode.GYM, TrainingMode.HOME, TrainingMode.MIXED),
            requiredEquipment = setOf("barbell", "dumbbells", "bands", "kettlebell"),
            priority = 10
        ),
        ExerciseCatalogItem(
            id = "hammer_curl",
            aliases = setOf("neutral_curl"),
            primaryPattern = MovementPattern.ARM_FLEXION,
            supportedModes = setOf(TrainingMode.GYM, TrainingMode.HOME, TrainingMode.MIXED),
            requiredEquipment = setOf("dumbbells", "kettlebell"),
            priority = 15
        ),
        ExerciseCatalogItem(
            id = "incline_curl",
            aliases = setOf("incline_dumbbell_curl"),
            primaryPattern = MovementPattern.ARM_FLEXION,
            supportedModes = setOf(TrainingMode.GYM, TrainingMode.MIXED),
            requiredEquipment = setOf("bench", "dumbbells"),
            priority = 25
        ),
        ExerciseCatalogItem(
            id = "concentration_curl",
            primaryPattern = MovementPattern.ARM_FLEXION,
            supportedModes = setOf(TrainingMode.GYM, TrainingMode.HOME, TrainingMode.MIXED),
            requiredEquipment = setOf("dumbbells"),
            priority = 30
        ),
        ExerciseCatalogItem(
            id = "cable_curl",
            primaryPattern = MovementPattern.ARM_FLEXION,
            supportedModes = setOf(TrainingMode.GYM, TrainingMode.MIXED),
            priority = 30
        ),
        ExerciseCatalogItem(
            id = "reverse_curl",
            primaryPattern = MovementPattern.ARM_FLEXION,
            supportedModes = setOf(TrainingMode.GYM, TrainingMode.HOME, TrainingMode.MIXED),
            requiredEquipment = setOf("barbell", "dumbbells", "bands"),
            priority = 40
        ),

        // ── ARM EXTENSION — triceps ──────────────────────────────────────────
        ExerciseCatalogItem(
            id = "tricep_extension",
            aliases = setOf("triceps_extension", "overhead_tricep_extension"),
            primaryPattern = MovementPattern.ARM_EXTENSION,
            supportedModes = setOf(TrainingMode.GYM, TrainingMode.HOME, TrainingMode.MIXED),
            requiredEquipment = setOf("barbell", "dumbbells", "bands", "kettlebell"),
            priority = 10
        ),
        ExerciseCatalogItem(
            id = "skull_crusher",
            aliases = setOf("lying_tricep_extension", "ez_bar_skull_crusher"),
            primaryPattern = MovementPattern.ARM_EXTENSION,
            supportedModes = setOf(TrainingMode.GYM, TrainingMode.MIXED),
            requiredEquipment = setOf("barbell", "dumbbells", "bench"),
            priority = 15
        ),
        ExerciseCatalogItem(
            id = "tricep_pushdown",
            aliases = setOf("cable_pushdown", "tricep_cable_pushdown"),
            primaryPattern = MovementPattern.ARM_EXTENSION,
            supportedModes = setOf(TrainingMode.GYM, TrainingMode.MIXED),
            priority = 20
        ),
        ExerciseCatalogItem(
            id = "close_grip_bench",
            aliases = setOf("close_grip_press"),
            primaryPattern = MovementPattern.ARM_EXTENSION,
            secondaryPatterns = setOf(MovementPattern.HORIZONTAL_PUSH),
            supportedModes = setOf(TrainingMode.GYM, TrainingMode.MIXED),
            requiredEquipment = setOf("barbell", "bench"),
            priority = 20
        ),
        ExerciseCatalogItem(
            id = "tricep_kickback",
            aliases = setOf("kickback"),
            primaryPattern = MovementPattern.ARM_EXTENSION,
            supportedModes = setOf(TrainingMode.GYM, TrainingMode.HOME, TrainingMode.MIXED),
            requiredEquipment = setOf("dumbbells", "bands"),
            priority = 35
        ),

        // ── CONDITIONING — cardio / fat loss ────────────────────────────────
        ExerciseCatalogItem(
            id = "run",
            aliases = setOf("running", "jogging"),
            primaryPattern = MovementPattern.CONDITIONING,
            supportedModes = setOf(TrainingMode.OUTDOOR, TrainingMode.HOME, TrainingMode.MIXED),
            priority = 10
        ),
        ExerciseCatalogItem(
            id = "sprint",
            aliases = setOf("sprinting", "interval_sprint"),
            primaryPattern = MovementPattern.CONDITIONING,
            supportedModes = setOf(TrainingMode.OUTDOOR, TrainingMode.GYM, TrainingMode.MIXED),
            priority = 15
        ),
        ExerciseCatalogItem(
            id = "bike",
            aliases = setOf("cycling", "stationary_bike"),
            primaryPattern = MovementPattern.CONDITIONING,
            supportedModes = setOf(TrainingMode.GYM, TrainingMode.HOME, TrainingMode.MIXED),
            requiredEquipment = setOf("cardio"),
            priority = 20
        ),
        ExerciseCatalogItem(
            id = "burpee",
            aliases = setOf("burpees"),
            primaryPattern = MovementPattern.CONDITIONING,
            secondaryPatterns = setOf(MovementPattern.HORIZONTAL_PUSH, MovementPattern.KNEE_DOMINANT),
            supportedModes = setOf(TrainingMode.HOME, TrainingMode.GYM, TrainingMode.OUTDOOR, TrainingMode.MIXED),
            priority = 15
        ),
        ExerciseCatalogItem(
            id = "jumping_jack",
            aliases = setOf("jumping_jacks"),
            primaryPattern = MovementPattern.CONDITIONING,
            supportedModes = setOf(TrainingMode.HOME, TrainingMode.GYM, TrainingMode.OUTDOOR, TrainingMode.MIXED),
            priority = 20
        ),
        ExerciseCatalogItem(
            id = "high_knees",
            primaryPattern = MovementPattern.CONDITIONING,
            supportedModes = setOf(TrainingMode.HOME, TrainingMode.GYM, TrainingMode.OUTDOOR, TrainingMode.MIXED),
            priority = 25
        ),
        ExerciseCatalogItem(
            id = "jump_rope",
            aliases = setOf("rope_jumping", "skipping"),
            primaryPattern = MovementPattern.CONDITIONING,
            supportedModes = setOf(TrainingMode.HOME, TrainingMode.GYM, TrainingMode.OUTDOOR, TrainingMode.MIXED),
            priority = 20
        ),
        ExerciseCatalogItem(
            id = "box_jump",
            aliases = setOf("box_jumps"),
            primaryPattern = MovementPattern.CONDITIONING,
            secondaryPatterns = setOf(MovementPattern.KNEE_DOMINANT),
            supportedModes = setOf(TrainingMode.HOME, TrainingMode.GYM, TrainingMode.OUTDOOR, TrainingMode.MIXED),
            priority = 25
        ),
        ExerciseCatalogItem(
            id = "rowing_machine",
            aliases = setOf("ergometer", "row_machine"),
            primaryPattern = MovementPattern.CONDITIONING,
            secondaryPatterns = setOf(MovementPattern.HORIZONTAL_PULL),
            supportedModes = setOf(TrainingMode.GYM, TrainingMode.HOME, TrainingMode.MIXED),
            requiredEquipment = setOf("cardio"),
            priority = 20
        ),
        ExerciseCatalogItem(
            id = "elliptical",
            aliases = setOf("cross_trainer"),
            primaryPattern = MovementPattern.CONDITIONING,
            supportedModes = setOf(TrainingMode.GYM, TrainingMode.MIXED),
            requiredEquipment = setOf("cardio"),
            priority = 30
        ),
        ExerciseCatalogItem(
            id = "stair_climb",
            aliases = setOf("stairmaster", "step_mill"),
            primaryPattern = MovementPattern.CONDITIONING,
            secondaryPatterns = setOf(MovementPattern.KNEE_DOMINANT),
            supportedModes = setOf(TrainingMode.GYM, TrainingMode.OUTDOOR, TrainingMode.MIXED),
            priority = 30
        ),
        ExerciseCatalogItem(
            id = "battle_rope",
            aliases = setOf("battle_ropes"),
            primaryPattern = MovementPattern.CONDITIONING,
            secondaryPatterns = setOf(MovementPattern.HORIZONTAL_PULL),
            supportedModes = setOf(TrainingMode.GYM, TrainingMode.MIXED),
            priority = 35
        ),
        ExerciseCatalogItem(
            id = "skater_jump",
            aliases = setOf("lateral_skater", "speed_skater"),
            primaryPattern = MovementPattern.CONDITIONING,
            secondaryPatterns = setOf(MovementPattern.SINGLE_LEG),
            supportedModes = setOf(TrainingMode.HOME, TrainingMode.GYM, TrainingMode.OUTDOOR, TrainingMode.MIXED),
            priority = 35
        ),
        ExerciseCatalogItem(
            id = "swim",
            aliases = setOf("swimming"),
            primaryPattern = MovementPattern.CONDITIONING,
            supportedModes = setOf(TrainingMode.OUTDOOR, TrainingMode.MIXED),
            priority = 40
        ),

        // ── MOBILITY — flexibility / recovery ───────────────────────────────
        ExerciseCatalogItem(
            id = "yoga",
            primaryPattern = MovementPattern.MOBILITY,
            secondaryPatterns = setOf(MovementPattern.CORE),
            supportedModes = setOf(TrainingMode.GYM, TrainingMode.HOME, TrainingMode.OUTDOOR, TrainingMode.MIXED),
            priority = 10
        ),
        ExerciseCatalogItem(
            id = "stretching",
            aliases = setOf("static_stretch", "flexibility"),
            primaryPattern = MovementPattern.MOBILITY,
            supportedModes = setOf(TrainingMode.HOME, TrainingMode.GYM, TrainingMode.OUTDOOR, TrainingMode.MIXED),
            priority = 15
        ),
        ExerciseCatalogItem(
            id = "foam_rolling",
            aliases = setOf("foam_roll", "myofascial_release"),
            primaryPattern = MovementPattern.MOBILITY,
            supportedModes = setOf(TrainingMode.HOME, TrainingMode.GYM, TrainingMode.MIXED),
            priority = 20
        ),
        ExerciseCatalogItem(
            id = "hip_flexor_stretch",
            aliases = setOf("couch_stretch", "kneeling_hip_flexor"),
            primaryPattern = MovementPattern.MOBILITY,
            supportedModes = setOf(TrainingMode.HOME, TrainingMode.GYM, TrainingMode.OUTDOOR, TrainingMode.MIXED),
            priority = 25
        ),
        ExerciseCatalogItem(
            id = "world_greatest_stretch",
            aliases = setOf("world_greatest"),
            primaryPattern = MovementPattern.MOBILITY,
            secondaryPatterns = setOf(MovementPattern.CORE),
            supportedModes = setOf(TrainingMode.HOME, TrainingMode.GYM, TrainingMode.OUTDOOR, TrainingMode.MIXED),
            priority = 25
        ),
        ExerciseCatalogItem(
            id = "cat_cow",
            primaryPattern = MovementPattern.MOBILITY,
            supportedModes = setOf(TrainingMode.HOME, TrainingMode.GYM, TrainingMode.MIXED),
            priority = 30
        )
    )

    private val canonicalIndex = items.associateBy { it.id }
    private val aliasIndex = items
        .flatMap { item -> item.aliases.map { alias -> normalizeCatalogToken(alias) to item } }
        .toMap()

    override fun all(): List<ExerciseCatalogItem> = items

    override fun supportedExerciseIds(): Set<String> = canonicalIndex.keys

    override fun findByIdOrAlias(rawToken: String): ExerciseCatalogItem? {
        val normalized = normalizeCatalogToken(rawToken)
        return canonicalIndex[normalized] ?: aliasIndex[normalized]
    }

    override fun availablePatterns(mode: TrainingMode, equipment: Set<String>): List<MovementPattern> =
        MovementPattern.entries.filter { pattern ->
            candidatesFor(pattern, mode, equipment).isNotEmpty()
        }

    override fun candidatesFor(
        pattern: MovementPattern,
        mode: TrainingMode,
        equipment: Set<String>
    ): List<ExerciseCatalogItem> {
        val scoped = items.filter { it.supports(pattern) }
        val byModeAndEquipment = scoped.filter { item ->
            supportsMode(item, mode) && matchesEquipment(item, mode, equipment)
        }
        if (byModeAndEquipment.isNotEmpty()) return byModeAndEquipment.sortedBy { it.priority }

        if (mode == TrainingMode.HOME) return emptyList()

        val relaxedEquipment = scoped.filter { item -> supportsMode(item, mode) }
        if (relaxedEquipment.isNotEmpty()) return relaxedEquipment.sortedBy { it.priority }

        val relaxedMode = scoped.filter { item -> matchesEquipment(item, mode, equipment) }
        if (relaxedMode.isNotEmpty()) return relaxedMode.sortedBy { it.priority }

        return scoped.sortedBy { it.priority }
    }

    private fun supportsMode(item: ExerciseCatalogItem, mode: TrainingMode): Boolean =
        when (mode) {
            TrainingMode.AUTO, TrainingMode.MIXED -> true
            else -> item.supportedModes.contains(mode) || item.supportedModes.contains(TrainingMode.MIXED)
        }

    private fun matchesEquipment(
        item: ExerciseCatalogItem,
        mode: TrainingMode,
        equipment: Set<String>
    ): Boolean {
        if (item.requiredEquipment.isEmpty()) return true
        if (mode == TrainingMode.GYM || mode == TrainingMode.OUTDOOR || mode == TrainingMode.MIXED) return true
        return equipment.any(item.requiredEquipment::contains)
    }

    private fun normalizeCatalogToken(rawToken: String): String =
        rawToken
            .trim()
            .lowercase()
            .replace(' ', '_')
            .replace('-', '_')
            .trim('_')
}
