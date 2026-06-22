package com.vtempe.server.features.ai.data.catalog

import com.vtempe.server.features.ai.domain.model.ExerciseCatalogItem
import com.vtempe.server.features.ai.domain.model.MovementPattern
import com.vtempe.server.features.ai.domain.model.TrainingMode
import com.vtempe.server.features.ai.domain.port.ExerciseCatalog

/**
 * Master exercise catalog — 107 exercises.
 *
 * difficulty: 1 = absolute beginner  2 = novice  3 = intermediate
 *             4 = advanced            5 = elite / requires years of practice
 *
 * Matched against AiProfile.experienceLevel (1–5) in the resolver so that
 * beginners receive wall-sits and goblet-squats, not pistol-squats and muscle-ups.
 *
 * priority: lower = preferred when resolver picks for a pattern.
 *   10 primary compound  20 strong alt  30 variation  40 accessory  50 machine/isolation
 */
class BuiltInExerciseCatalog : ExerciseCatalog {

    private val items = listOf(

        // ── KNEE DOMINANT ──────────────────────────────────────────────────────
        ExerciseCatalogItem("squat", setOf("back_squat","barbell_squat"),
            MovementPattern.KNEE_DOMINANT, setOf(),
            setOf(TrainingMode.GYM, TrainingMode.MIXED),
            setOf("barbell","rack"), priority=10, difficulty=3),

        ExerciseCatalogItem("goblet_squat", setOf("goblet"),
            MovementPattern.KNEE_DOMINANT, setOf(),
            setOf(TrainingMode.GYM, TrainingMode.HOME, TrainingMode.MIXED),
            setOf("dumbbells","kettlebell"), priority=15, difficulty=2),

        ExerciseCatalogItem("front_squat", setOf("frontsquat"),
            MovementPattern.KNEE_DOMINANT, setOf(),
            setOf(TrainingMode.GYM, TrainingMode.MIXED),
            setOf("barbell"), priority=20, difficulty=4),

        ExerciseCatalogItem("leg_press", setOf("legpress"),
            MovementPattern.KNEE_DOMINANT, setOf(),
            setOf(TrainingMode.GYM, TrainingMode.MIXED),
            priority=25, difficulty=1),

        ExerciseCatalogItem("sumo_squat", setOf("plie_squat","wide_squat"),
            MovementPattern.KNEE_DOMINANT, setOf(MovementPattern.HINGE),
            setOf(TrainingMode.GYM, TrainingMode.HOME, TrainingMode.OUTDOOR, TrainingMode.MIXED),
            priority=30, difficulty=2),

        ExerciseCatalogItem("wall_sit", setOf("wall_squat"),
            MovementPattern.KNEE_DOMINANT, setOf(),
            setOf(TrainingMode.HOME, TrainingMode.OUTDOOR, TrainingMode.GYM, TrainingMode.MIXED),
            priority=35, difficulty=1),

        ExerciseCatalogItem("box_squat", emptySet(),
            MovementPattern.KNEE_DOMINANT, setOf(),
            setOf(TrainingMode.GYM, TrainingMode.MIXED),
            setOf("barbell","rack"), priority=30, difficulty=3),

        ExerciseCatalogItem("leg_extension", setOf("quad_extension"),
            MovementPattern.KNEE_DOMINANT, setOf(),
            setOf(TrainingMode.GYM, TrainingMode.MIXED),
            priority=50, difficulty=1),

        ExerciseCatalogItem("jump_squat", setOf("squat_jump"),
            MovementPattern.KNEE_DOMINANT, setOf(MovementPattern.CONDITIONING),
            setOf(TrainingMode.HOME, TrainingMode.OUTDOOR, TrainingMode.GYM, TrainingMode.MIXED),
            priority=40, difficulty=2),

        ExerciseCatalogItem("hack_squat", setOf("machine_hack_squat"),
            MovementPattern.KNEE_DOMINANT, setOf(),
            setOf(TrainingMode.GYM, TrainingMode.MIXED),
            priority=40, difficulty=1),

        ExerciseCatalogItem("barbell_hack_squat", setOf("hack_lift","behind_leg_squat"),
            MovementPattern.KNEE_DOMINANT, setOf(MovementPattern.HINGE),
            setOf(TrainingMode.GYM, TrainingMode.MIXED),
            setOf("barbell"), priority=45, difficulty=3),

        // ── HINGE ──────────────────────────────────────────────────────────────
        ExerciseCatalogItem("deadlift", setOf("conventional_deadlift"),
            MovementPattern.HINGE, setOf(),
            setOf(TrainingMode.GYM, TrainingMode.MIXED),
            setOf("barbell"), priority=10, difficulty=3),

        ExerciseCatalogItem("romanian_deadlift", setOf("rdl","stiff_leg_deadlift"),
            MovementPattern.HINGE, setOf(),
            setOf(TrainingMode.GYM, TrainingMode.HOME, TrainingMode.MIXED),
            setOf("barbell","dumbbells"), priority=15, difficulty=3),

        ExerciseCatalogItem("hip_thrust", setOf("hipthrust","hip_thrusts","barbell_hip_thrust"),
            MovementPattern.HINGE, setOf(),
            setOf(TrainingMode.GYM, TrainingMode.HOME, TrainingMode.MIXED),
            priority=20, difficulty=2),

        ExerciseCatalogItem("glute_bridge", setOf("bridge"),
            MovementPattern.HINGE, setOf(),
            setOf(TrainingMode.HOME, TrainingMode.GYM, TrainingMode.OUTDOOR, TrainingMode.MIXED),
            priority=25, difficulty=1),

        ExerciseCatalogItem("kettlebell_swing", setOf("kb_swing","swing"),
            MovementPattern.HINGE, setOf(MovementPattern.CONDITIONING),
            setOf(TrainingMode.HOME, TrainingMode.GYM, TrainingMode.OUTDOOR, TrainingMode.MIXED),
            setOf("kettlebell","dumbbells"), priority=20, difficulty=3),

        ExerciseCatalogItem("single_leg_deadlift", setOf("single_leg_rdl","sl_deadlift"),
            MovementPattern.HINGE, setOf(MovementPattern.SINGLE_LEG),
            setOf(TrainingMode.HOME, TrainingMode.GYM, TrainingMode.OUTDOOR, TrainingMode.MIXED),
            priority=30, difficulty=4),

        ExerciseCatalogItem("sumo_deadlift", emptySet(),
            MovementPattern.HINGE, setOf(),
            setOf(TrainingMode.GYM, TrainingMode.MIXED),
            setOf("barbell"), priority=30, difficulty=3),

        ExerciseCatalogItem("good_morning", emptySet(),
            MovementPattern.HINGE, setOf(),
            setOf(TrainingMode.GYM, TrainingMode.MIXED),
            setOf("barbell"), priority=40, difficulty=3),

        ExerciseCatalogItem("nordic_curl", setOf("nordic_hamstring"),
            MovementPattern.HINGE, setOf(),
            setOf(TrainingMode.HOME, TrainingMode.GYM, TrainingMode.OUTDOOR, TrainingMode.MIXED),
            priority=35, difficulty=4),

        ExerciseCatalogItem("leg_curl", setOf("hamstring_curl"),
            MovementPattern.HINGE, setOf(),
            setOf(TrainingMode.GYM, TrainingMode.MIXED),
            priority=50, difficulty=1),

        // ── SINGLE LEG ─────────────────────────────────────────────────────────
        ExerciseCatalogItem("lunge", setOf("walking_lunge","forward_lunge"),
            MovementPattern.SINGLE_LEG, setOf(MovementPattern.KNEE_DOMINANT),
            setOf(TrainingMode.GYM, TrainingMode.HOME, TrainingMode.OUTDOOR, TrainingMode.MIXED),
            priority=10, difficulty=2),

        ExerciseCatalogItem("reverse_lunge", setOf("step_back_lunge"),
            MovementPattern.SINGLE_LEG, setOf(),
            setOf(TrainingMode.HOME, TrainingMode.GYM, TrainingMode.OUTDOOR, TrainingMode.MIXED),
            priority=15, difficulty=2),

        ExerciseCatalogItem("bulgarian_split_squat",
            setOf("split_squat","rear_elevated_split_squat","ress"),
            MovementPattern.SINGLE_LEG, setOf(MovementPattern.KNEE_DOMINANT),
            setOf(TrainingMode.HOME, TrainingMode.GYM, TrainingMode.MIXED),
            priority=15, difficulty=4),

        ExerciseCatalogItem("step_up", setOf("box_step_up"),
            MovementPattern.SINGLE_LEG, setOf(MovementPattern.KNEE_DOMINANT),
            setOf(TrainingMode.HOME, TrainingMode.GYM, TrainingMode.OUTDOOR, TrainingMode.MIXED),
            priority=20, difficulty=2),

        ExerciseCatalogItem("lateral_lunge", setOf("side_lunge"),
            MovementPattern.SINGLE_LEG, setOf(),
            setOf(TrainingMode.HOME, TrainingMode.GYM, TrainingMode.OUTDOOR, TrainingMode.MIXED),
            priority=30, difficulty=2),

        ExerciseCatalogItem("pistol_squat", setOf("single_leg_squat"),
            MovementPattern.SINGLE_LEG, setOf(),
            setOf(TrainingMode.HOME, TrainingMode.GYM, TrainingMode.OUTDOOR, TrainingMode.MIXED),
            priority=35, difficulty=5),

        ExerciseCatalogItem("skater_lunge", setOf("skater_squat","curtsy_lunge"),
            MovementPattern.SINGLE_LEG, setOf(),
            setOf(TrainingMode.HOME, TrainingMode.GYM, TrainingMode.OUTDOOR, TrainingMode.MIXED),
            priority=40, difficulty=3),

        // ── HORIZONTAL PUSH ────────────────────────────────────────────────────
        ExerciseCatalogItem("bench", setOf("bench_press","flat_bench"),
            MovementPattern.HORIZONTAL_PUSH, setOf(),
            setOf(TrainingMode.GYM, TrainingMode.MIXED),
            setOf("bench","barbell","dumbbells"), priority=10, difficulty=2),

        ExerciseCatalogItem("incline_bench", setOf("incline_press"),
            MovementPattern.HORIZONTAL_PUSH, setOf(),
            setOf(TrainingMode.GYM, TrainingMode.MIXED),
            setOf("bench","barbell","dumbbells"), priority=20, difficulty=2),

        ExerciseCatalogItem("pushup", setOf("push_up","push_ups"),
            MovementPattern.HORIZONTAL_PUSH, setOf(),
            setOf(TrainingMode.GYM, TrainingMode.HOME, TrainingMode.OUTDOOR, TrainingMode.MIXED),
            priority=20, difficulty=1),

        ExerciseCatalogItem("dip", setOf("parallel_bar_dip","chest_dip"),
            MovementPattern.HORIZONTAL_PUSH, setOf(MovementPattern.ARM_EXTENSION),
            setOf(TrainingMode.GYM, TrainingMode.HOME, TrainingMode.OUTDOOR, TrainingMode.MIXED),
            setOf("bench","trx","pullup_bar"), priority=25, difficulty=3),

        ExerciseCatalogItem("diamond_pushup", setOf("close_grip_pushup","tricep_pushup"),
            MovementPattern.HORIZONTAL_PUSH, setOf(MovementPattern.ARM_EXTENSION),
            setOf(TrainingMode.HOME, TrainingMode.GYM, TrainingMode.OUTDOOR, TrainingMode.MIXED),
            priority=30, difficulty=2),

        ExerciseCatalogItem("wide_pushup", setOf("wide_grip_pushup"),
            MovementPattern.HORIZONTAL_PUSH, setOf(),
            setOf(TrainingMode.HOME, TrainingMode.GYM, TrainingMode.OUTDOOR, TrainingMode.MIXED),
            priority=30, difficulty=1),

        ExerciseCatalogItem("decline_pushup", emptySet(),
            MovementPattern.HORIZONTAL_PUSH, setOf(),
            setOf(TrainingMode.HOME, TrainingMode.OUTDOOR, TrainingMode.GYM, TrainingMode.MIXED),
            priority=35, difficulty=2),

        ExerciseCatalogItem("incline_pushup", setOf("elevated_pushup"),
            MovementPattern.HORIZONTAL_PUSH, setOf(),
            setOf(TrainingMode.HOME, TrainingMode.OUTDOOR, TrainingMode.GYM, TrainingMode.MIXED),
            priority=40, difficulty=1),

        ExerciseCatalogItem("dumbbell_fly", setOf("chest_fly","db_fly"),
            MovementPattern.HORIZONTAL_PUSH, setOf(),
            setOf(TrainingMode.GYM, TrainingMode.HOME, TrainingMode.MIXED),
            setOf("dumbbells","bench"), priority=40, difficulty=2),

        ExerciseCatalogItem("cable_fly", setOf("cable_chest_fly"),
            MovementPattern.HORIZONTAL_PUSH, setOf(),
            setOf(TrainingMode.GYM, TrainingMode.MIXED),
            priority=45, difficulty=2),

        ExerciseCatalogItem("pike_pushup", setOf("pike_push_up"),
            MovementPattern.HORIZONTAL_PUSH, setOf(MovementPattern.VERTICAL_PUSH),
            setOf(TrainingMode.HOME, TrainingMode.OUTDOOR, TrainingMode.GYM, TrainingMode.MIXED),
            priority=35, difficulty=2),

        ExerciseCatalogItem("close_grip_bench", setOf("close_grip_press"),
            MovementPattern.HORIZONTAL_PUSH, setOf(MovementPattern.ARM_EXTENSION),
            setOf(TrainingMode.GYM, TrainingMode.MIXED),
            setOf("barbell","bench"), priority=30, difficulty=3),

        // ── HORIZONTAL PULL ────────────────────────────────────────────────────
        ExerciseCatalogItem("row", setOf("bent_over_row","barbell_row"),
            MovementPattern.HORIZONTAL_PULL, setOf(),
            setOf(TrainingMode.GYM, TrainingMode.HOME, TrainingMode.MIXED),
            setOf("barbell","dumbbells","bands","trx"), priority=10, difficulty=3),

        ExerciseCatalogItem("dumbbell_row", setOf("db_row","one_arm_row","single_arm_row"),
            MovementPattern.HORIZONTAL_PULL, setOf(),
            setOf(TrainingMode.GYM, TrainingMode.HOME, TrainingMode.MIXED),
            setOf("dumbbells"), priority=15, difficulty=2),

        ExerciseCatalogItem("inverted_row", setOf("bodyweight_row","australian_pullup"),
            MovementPattern.HORIZONTAL_PULL, setOf(),
            setOf(TrainingMode.HOME, TrainingMode.GYM, TrainingMode.OUTDOOR, TrainingMode.MIXED),
            setOf("pullup_bar","trx"), priority=20, difficulty=2),

        ExerciseCatalogItem("cable_row", setOf("seated_cable_row","low_cable_row"),
            MovementPattern.HORIZONTAL_PULL, setOf(),
            setOf(TrainingMode.GYM, TrainingMode.MIXED),
            priority=20, difficulty=1),

        ExerciseCatalogItem("band_row", setOf("resistance_band_row"),
            MovementPattern.HORIZONTAL_PULL, setOf(),
            setOf(TrainingMode.HOME, TrainingMode.OUTDOOR, TrainingMode.MIXED),
            setOf("bands"), priority=30, difficulty=1),

        ExerciseCatalogItem("face_pull", setOf("cable_face_pull"),
            MovementPattern.HORIZONTAL_PULL, setOf(),
            setOf(TrainingMode.GYM, TrainingMode.HOME, TrainingMode.MIXED),
            setOf("bands"), priority=35, difficulty=2),

        ExerciseCatalogItem("chest_supported_row", setOf("incline_row"),
            MovementPattern.HORIZONTAL_PULL, setOf(),
            setOf(TrainingMode.GYM, TrainingMode.MIXED),
            setOf("bench","dumbbells"), priority=30, difficulty=2),

        ExerciseCatalogItem("t_bar_row", setOf("tbar_row"),
            MovementPattern.HORIZONTAL_PULL, setOf(),
            setOf(TrainingMode.GYM, TrainingMode.MIXED),
            setOf("barbell"), priority=30, difficulty=3),

        ExerciseCatalogItem("rowing_machine", setOf("ergometer","row_machine"),
            MovementPattern.CONDITIONING, setOf(MovementPattern.HORIZONTAL_PULL),
            setOf(TrainingMode.GYM, TrainingMode.HOME, TrainingMode.MIXED),
            setOf("cardio"), priority=20, difficulty=2),

        ExerciseCatalogItem("battle_rope", setOf("battle_ropes"),
            MovementPattern.CONDITIONING, setOf(MovementPattern.HORIZONTAL_PULL),
            setOf(TrainingMode.GYM, TrainingMode.MIXED),
            priority=35, difficulty=2),

        // ── VERTICAL PUSH ──────────────────────────────────────────────────────
        ExerciseCatalogItem("ohp", setOf("overhead_press","military_press","standing_press"),
            MovementPattern.VERTICAL_PUSH, setOf(),
            setOf(TrainingMode.GYM, TrainingMode.HOME, TrainingMode.MIXED),
            setOf("barbell","dumbbells","bands","kettlebell"), priority=10, difficulty=3),

        ExerciseCatalogItem("dumbbell_shoulder_press",
            setOf("db_shoulder_press","seated_dumbbell_press"),
            MovementPattern.VERTICAL_PUSH, setOf(),
            setOf(TrainingMode.GYM, TrainingMode.HOME, TrainingMode.MIXED),
            setOf("dumbbells"), priority=15, difficulty=2),

        ExerciseCatalogItem("arnold_press", emptySet(),
            MovementPattern.VERTICAL_PUSH, setOf(),
            setOf(TrainingMode.GYM, TrainingMode.HOME, TrainingMode.MIXED),
            setOf("dumbbells"), priority=25, difficulty=3),

        ExerciseCatalogItem("lateral_raise", setOf("side_raise","lateral_delt_raise"),
            MovementPattern.VERTICAL_PUSH, setOf(),
            setOf(TrainingMode.GYM, TrainingMode.HOME, TrainingMode.MIXED),
            setOf("dumbbells","bands","kettlebell"), priority=30, difficulty=1),

        ExerciseCatalogItem("front_raise", setOf("anterior_raise"),
            MovementPattern.VERTICAL_PUSH, setOf(),
            setOf(TrainingMode.GYM, TrainingMode.HOME, TrainingMode.MIXED),
            setOf("dumbbells","bands"), priority=40, difficulty=1),

        ExerciseCatalogItem("handstand_pushup", setOf("hspu","wall_handstand_pushup"),
            MovementPattern.VERTICAL_PUSH, setOf(),
            setOf(TrainingMode.HOME, TrainingMode.GYM, TrainingMode.OUTDOOR, TrainingMode.MIXED),
            priority=35, difficulty=5),

        ExerciseCatalogItem("upright_row", emptySet(),
            MovementPattern.VERTICAL_PUSH, setOf(MovementPattern.HORIZONTAL_PULL),
            setOf(TrainingMode.GYM, TrainingMode.HOME, TrainingMode.MIXED),
            setOf("barbell","dumbbells","bands"), priority=40, difficulty=2),

        // ── VERTICAL PULL ──────────────────────────────────────────────────────
        ExerciseCatalogItem("pullup", setOf("pull_up","pullups","overhand_pullup"),
            MovementPattern.VERTICAL_PULL, setOf(),
            setOf(TrainingMode.GYM, TrainingMode.HOME, TrainingMode.OUTDOOR, TrainingMode.MIXED),
            setOf("pullup_bar","trx"), priority=10, difficulty=3),

        ExerciseCatalogItem("chin_up", setOf("chinup","supinated_pullup","underhand_pullup"),
            MovementPattern.VERTICAL_PULL, setOf(MovementPattern.ARM_FLEXION),
            setOf(TrainingMode.GYM, TrainingMode.HOME, TrainingMode.OUTDOOR, TrainingMode.MIXED),
            setOf("pullup_bar"), priority=15, difficulty=3),

        ExerciseCatalogItem("wide_pullup", setOf("wide_grip_pullup"),
            MovementPattern.VERTICAL_PULL, setOf(),
            setOf(TrainingMode.GYM, TrainingMode.HOME, TrainingMode.OUTDOOR, TrainingMode.MIXED),
            setOf("pullup_bar"), priority=20, difficulty=4),

        ExerciseCatalogItem("lat_pulldown", setOf("cable_pulldown","pulldown"),
            MovementPattern.VERTICAL_PULL, setOf(),
            setOf(TrainingMode.GYM, TrainingMode.MIXED),
            priority=20, difficulty=1),

        ExerciseCatalogItem("band_pulldown", setOf("resistance_band_pulldown"),
            MovementPattern.VERTICAL_PULL, setOf(),
            setOf(TrainingMode.HOME, TrainingMode.GYM, TrainingMode.MIXED),
            setOf("bands"), priority=30, difficulty=1),

        ExerciseCatalogItem("assisted_pullup", setOf("band_assisted_pullup"),
            MovementPattern.VERTICAL_PULL, setOf(),
            setOf(TrainingMode.GYM, TrainingMode.HOME, TrainingMode.MIXED),
            setOf("bands"), priority=30, difficulty=2),

        ExerciseCatalogItem("muscle_up", emptySet(),
            MovementPattern.VERTICAL_PULL, setOf(MovementPattern.HORIZONTAL_PUSH),
            setOf(TrainingMode.GYM, TrainingMode.OUTDOOR, TrainingMode.MIXED),
            setOf("pullup_bar"), priority=35, difficulty=5),

        // ── CORE ───────────────────────────────────────────────────────────────
        ExerciseCatalogItem("plank", setOf("plank_hold"),
            MovementPattern.CORE, setOf(),
            setOf(TrainingMode.GYM, TrainingMode.HOME, TrainingMode.OUTDOOR, TrainingMode.MIXED),
            priority=10, difficulty=1),

        ExerciseCatalogItem("side_plank", emptySet(),
            MovementPattern.CORE, setOf(),
            setOf(TrainingMode.HOME, TrainingMode.GYM, TrainingMode.OUTDOOR, TrainingMode.MIXED),
            priority=20, difficulty=2),

        ExerciseCatalogItem("crunch", setOf("ab_crunch"),
            MovementPattern.CORE, setOf(),
            setOf(TrainingMode.HOME, TrainingMode.GYM, TrainingMode.OUTDOOR, TrainingMode.MIXED),
            priority=20, difficulty=1),

        ExerciseCatalogItem("bicycle_crunch", emptySet(),
            MovementPattern.CORE, setOf(),
            setOf(TrainingMode.HOME, TrainingMode.GYM, TrainingMode.OUTDOOR, TrainingMode.MIXED),
            priority=25, difficulty=1),

        ExerciseCatalogItem("leg_raise", setOf("lying_leg_raise"),
            MovementPattern.CORE, setOf(),
            setOf(TrainingMode.HOME, TrainingMode.GYM, TrainingMode.OUTDOOR, TrainingMode.MIXED),
            priority=25, difficulty=2),

        ExerciseCatalogItem("hanging_leg_raise", setOf("hanging_knee_raise"),
            MovementPattern.CORE, setOf(),
            setOf(TrainingMode.HOME, TrainingMode.GYM, TrainingMode.OUTDOOR, TrainingMode.MIXED),
            setOf("pullup_bar"), priority=20, difficulty=3),

        ExerciseCatalogItem("toes_to_bar", setOf("toes2bar","ttb"),
            MovementPattern.CORE, setOf(MovementPattern.VERTICAL_PULL),
            setOf(TrainingMode.HOME, TrainingMode.GYM, TrainingMode.OUTDOOR, TrainingMode.MIXED),
            setOf("pullup_bar"), priority=25, difficulty=4),

        ExerciseCatalogItem("mountain_climber", setOf("mountain_climbers"),
            MovementPattern.CORE, setOf(MovementPattern.CONDITIONING),
            setOf(TrainingMode.HOME, TrainingMode.GYM, TrainingMode.OUTDOOR, TrainingMode.MIXED),
            priority=30, difficulty=2),

        ExerciseCatalogItem("russian_twist", emptySet(),
            MovementPattern.CORE, setOf(),
            setOf(TrainingMode.HOME, TrainingMode.GYM, TrainingMode.OUTDOOR, TrainingMode.MIXED),
            priority=30, difficulty=1),

        ExerciseCatalogItem("dead_bug", emptySet(),
            MovementPattern.CORE, setOf(),
            setOf(TrainingMode.HOME, TrainingMode.GYM, TrainingMode.MIXED),
            priority=25, difficulty=2),

        ExerciseCatalogItem("hollow_body", setOf("hollow_hold"),
            MovementPattern.CORE, setOf(),
            setOf(TrainingMode.HOME, TrainingMode.GYM, TrainingMode.OUTDOOR, TrainingMode.MIXED),
            priority=30, difficulty=3),

        ExerciseCatalogItem("v_up", setOf("vup","jackknife"),
            MovementPattern.CORE, setOf(),
            setOf(TrainingMode.HOME, TrainingMode.GYM, TrainingMode.OUTDOOR, TrainingMode.MIXED),
            priority=35, difficulty=2),

        ExerciseCatalogItem("cable_crunch", setOf("kneeling_cable_crunch"),
            MovementPattern.CORE, setOf(),
            setOf(TrainingMode.GYM, TrainingMode.MIXED),
            priority=40, difficulty=2),

        ExerciseCatalogItem("ab_wheel", setOf("rollout"),
            MovementPattern.CORE, setOf(),
            setOf(TrainingMode.HOME, TrainingMode.GYM, TrainingMode.MIXED),
            priority=35, difficulty=3),

        ExerciseCatalogItem("l_sit", emptySet(),
            MovementPattern.CORE, setOf(MovementPattern.ARM_EXTENSION),
            setOf(TrainingMode.HOME, TrainingMode.GYM, TrainingMode.OUTDOOR, TrainingMode.MIXED),
            setOf("pullup_bar"), priority=40, difficulty=5),

        // ── ARM FLEXION / BICEPS ───────────────────────────────────────────────
        ExerciseCatalogItem("curl", setOf("bicep_curl","biceps_curl","barbell_curl"),
            MovementPattern.ARM_FLEXION, setOf(),
            setOf(TrainingMode.GYM, TrainingMode.HOME, TrainingMode.MIXED),
            setOf("barbell","dumbbells","bands","kettlebell"), priority=10, difficulty=1),

        ExerciseCatalogItem("hammer_curl", setOf("neutral_curl"),
            MovementPattern.ARM_FLEXION, setOf(),
            setOf(TrainingMode.GYM, TrainingMode.HOME, TrainingMode.MIXED),
            setOf("dumbbells","kettlebell"), priority=15, difficulty=1),

        ExerciseCatalogItem("incline_curl", setOf("incline_dumbbell_curl"),
            MovementPattern.ARM_FLEXION, setOf(),
            setOf(TrainingMode.GYM, TrainingMode.MIXED),
            setOf("bench","dumbbells"), priority=25, difficulty=2),

        ExerciseCatalogItem("concentration_curl", emptySet(),
            MovementPattern.ARM_FLEXION, setOf(),
            setOf(TrainingMode.GYM, TrainingMode.HOME, TrainingMode.MIXED),
            setOf("dumbbells"), priority=30, difficulty=1),

        ExerciseCatalogItem("cable_curl", emptySet(),
            MovementPattern.ARM_FLEXION, setOf(),
            setOf(TrainingMode.GYM, TrainingMode.MIXED),
            priority=30, difficulty=1),

        ExerciseCatalogItem("reverse_curl", emptySet(),
            MovementPattern.ARM_FLEXION, setOf(),
            setOf(TrainingMode.GYM, TrainingMode.HOME, TrainingMode.MIXED),
            setOf("barbell","dumbbells","bands"), priority=40, difficulty=2),

        // ── ARM EXTENSION / TRICEPS ────────────────────────────────────────────
        ExerciseCatalogItem("tricep_extension",
            setOf("triceps_extension","overhead_tricep_extension"),
            MovementPattern.ARM_EXTENSION, setOf(),
            setOf(TrainingMode.GYM, TrainingMode.HOME, TrainingMode.MIXED),
            setOf("barbell","dumbbells","bands","kettlebell"), priority=10, difficulty=1),

        ExerciseCatalogItem("skull_crusher",
            setOf("lying_tricep_extension","ez_bar_skull_crusher"),
            MovementPattern.ARM_EXTENSION, setOf(),
            setOf(TrainingMode.GYM, TrainingMode.MIXED),
            setOf("barbell","dumbbells","bench"), priority=15, difficulty=2),

        ExerciseCatalogItem("tricep_pushdown",
            setOf("cable_pushdown","tricep_cable_pushdown"),
            MovementPattern.ARM_EXTENSION, setOf(),
            setOf(TrainingMode.GYM, TrainingMode.MIXED),
            priority=20, difficulty=1),

        ExerciseCatalogItem("tricep_kickback", setOf("kickback"),
            MovementPattern.ARM_EXTENSION, setOf(),
            setOf(TrainingMode.GYM, TrainingMode.HOME, TrainingMode.MIXED),
            setOf("dumbbells","bands"), priority=35, difficulty=1),

        // ── CONDITIONING / CARDIO ──────────────────────────────────────────────
        ExerciseCatalogItem("run", setOf("running","jogging"),
            MovementPattern.CONDITIONING, setOf(),
            setOf(TrainingMode.OUTDOOR, TrainingMode.HOME, TrainingMode.MIXED),
            priority=10, difficulty=1),

        ExerciseCatalogItem("sprint", setOf("sprinting","interval_sprint"),
            MovementPattern.CONDITIONING, setOf(),
            setOf(TrainingMode.OUTDOOR, TrainingMode.GYM, TrainingMode.MIXED),
            priority=15, difficulty=2),

        ExerciseCatalogItem("bike", setOf("cycling","stationary_bike"),
            MovementPattern.CONDITIONING, setOf(),
            setOf(TrainingMode.GYM, TrainingMode.HOME, TrainingMode.MIXED),
            setOf("cardio"), priority=20, difficulty=1),

        ExerciseCatalogItem("burpee", setOf("burpees"),
            MovementPattern.CONDITIONING,
            setOf(MovementPattern.HORIZONTAL_PUSH, MovementPattern.KNEE_DOMINANT),
            setOf(TrainingMode.HOME, TrainingMode.GYM, TrainingMode.OUTDOOR, TrainingMode.MIXED),
            priority=15, difficulty=2),

        ExerciseCatalogItem("jumping_jack", setOf("jumping_jacks"),
            MovementPattern.CONDITIONING, setOf(),
            setOf(TrainingMode.HOME, TrainingMode.GYM, TrainingMode.OUTDOOR, TrainingMode.MIXED),
            priority=20, difficulty=1),

        ExerciseCatalogItem("high_knees", emptySet(),
            MovementPattern.CONDITIONING, setOf(),
            setOf(TrainingMode.HOME, TrainingMode.GYM, TrainingMode.OUTDOOR, TrainingMode.MIXED),
            priority=25, difficulty=1),

        ExerciseCatalogItem("jump_rope", setOf("rope_jumping","skipping"),
            MovementPattern.CONDITIONING, setOf(),
            setOf(TrainingMode.HOME, TrainingMode.GYM, TrainingMode.OUTDOOR, TrainingMode.MIXED),
            priority=20, difficulty=2),

        ExerciseCatalogItem("box_jump", setOf("box_jumps"),
            MovementPattern.CONDITIONING, setOf(MovementPattern.KNEE_DOMINANT),
            setOf(TrainingMode.HOME, TrainingMode.GYM, TrainingMode.OUTDOOR, TrainingMode.MIXED),
            priority=25, difficulty=3),

        ExerciseCatalogItem("elliptical", setOf("cross_trainer"),
            MovementPattern.CONDITIONING, setOf(),
            setOf(TrainingMode.GYM, TrainingMode.MIXED),
            setOf("cardio"), priority=30, difficulty=1),

        ExerciseCatalogItem("stair_climb", setOf("stairmaster","step_mill"),
            MovementPattern.CONDITIONING, setOf(MovementPattern.KNEE_DOMINANT),
            setOf(TrainingMode.GYM, TrainingMode.OUTDOOR, TrainingMode.MIXED),
            priority=30, difficulty=1),

        ExerciseCatalogItem("skater_jump", setOf("lateral_skater","speed_skater"),
            MovementPattern.CONDITIONING, setOf(MovementPattern.SINGLE_LEG),
            setOf(TrainingMode.HOME, TrainingMode.GYM, TrainingMode.OUTDOOR, TrainingMode.MIXED),
            priority=35, difficulty=2),

        ExerciseCatalogItem("swim", setOf("swimming"),
            MovementPattern.CONDITIONING, setOf(),
            setOf(TrainingMode.OUTDOOR, TrainingMode.MIXED),
            priority=40, difficulty=3),

        // ── MOBILITY ───────────────────────────────────────────────────────────
        ExerciseCatalogItem("stretching", setOf("static_stretch","flexibility"),
            MovementPattern.MOBILITY, setOf(),
            setOf(TrainingMode.HOME, TrainingMode.GYM, TrainingMode.OUTDOOR, TrainingMode.MIXED),
            priority=15, difficulty=1),

        ExerciseCatalogItem("foam_rolling", setOf("foam_roll","myofascial_release"),
            MovementPattern.MOBILITY, setOf(),
            setOf(TrainingMode.HOME, TrainingMode.GYM, TrainingMode.MIXED),
            priority=20, difficulty=1),

        ExerciseCatalogItem("hip_flexor_stretch",
            setOf("couch_stretch","kneeling_hip_flexor"),
            MovementPattern.MOBILITY, setOf(),
            setOf(TrainingMode.HOME, TrainingMode.GYM, TrainingMode.OUTDOOR, TrainingMode.MIXED),
            priority=25, difficulty=1),

        ExerciseCatalogItem("world_greatest_stretch", setOf("world_greatest"),
            MovementPattern.MOBILITY, setOf(MovementPattern.CORE),
            setOf(TrainingMode.HOME, TrainingMode.GYM, TrainingMode.OUTDOOR, TrainingMode.MIXED),
            priority=25, difficulty=2),

        ExerciseCatalogItem("cat_cow", emptySet(),
            MovementPattern.MOBILITY, setOf(),
            setOf(TrainingMode.HOME, TrainingMode.GYM, TrainingMode.MIXED),
            priority=30, difficulty=1)
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
            // MIXED in supportedModes means the exercise is usable in a mixed gym+home context,
            // NOT that it's available at home. Only exercises explicitly listing HOME are home-safe.
            else -> item.supportedModes.contains(mode)
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
        rawToken.trim().lowercase().replace(' ', '_').replace('-', '_').trim('_')
}
