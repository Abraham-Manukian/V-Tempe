package com.vtempe.ui.screens

import com.vtempe.shared.domain.model.CoachTrainerIds
import com.vtempe.ui.Res
import com.vtempe.ui.*
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.StringResource

data class CoachTrainerUi(
    val id: String,
    val nameRes: StringResource,
    val avatar: DrawableResource
)

val coachTrainerOptions: List<CoachTrainerUi> = listOf(
    CoachTrainerUi(CoachTrainerIds.ARTUR, Res.string.coach_artur_name, Res.drawable.coach_artur_avatar),
    CoachTrainerUi(CoachTrainerIds.MIA, Res.string.coach_mia_name, Res.drawable.coach_mia_avatar),
    CoachTrainerUi(CoachTrainerIds.VTEMPE, Res.string.coach_vtempe_name, Res.drawable.coach_vtempe_avatar)
)

interface CoachVisualProvider {
    fun avatar(trainerId: String): DrawableResource
    fun exerciseIllustration(
        trainerId: String,
        exerciseId: String,
        fallback: DrawableResource
    ): DrawableResource
}

object DefaultCoachVisualProvider : CoachVisualProvider {
    override fun avatar(trainerId: String): DrawableResource =
        coachTrainerOptions.firstOrNull { it.id == normalizeCoachTrainerId(trainerId) }?.avatar
            ?: Res.drawable.coach_mia_avatar

    override fun exerciseIllustration(
        trainerId: String,
        exerciseId: String,
        fallback: DrawableResource
    ): DrawableResource {
        val normalizedTrainer = normalizeCoachTrainerId(trainerId)
        // Resolve visual alias so variant exercises share the "parent" exercise photo
        val normalizedExercise = exerciseId.lowercase().replace('-', '_')
        val lookupId = exerciseVisualAlias[normalizedExercise] ?: normalizedExercise
        return when (normalizedTrainer) {
            CoachTrainerIds.ARTUR -> arturExerciseIllustrations[lookupId]
            CoachTrainerIds.VTEMPE -> vtempeExerciseIllustrations[lookupId]
            else -> miaExerciseIllustrations[lookupId]
        } ?: fallback
    }
}

/**
 * Maps exercise IDs that don't have their own photo to the most visually similar
 * exercise that does. This avoids generating hundreds of redundant coach photos —
 * e.g. goblet_squat looks like a squat, chin_up looks like a pullup.
 *
 * Exercises not listed here fall through to the SVG illustration fallback.
 */
private val exerciseVisualAlias: Map<String, String> = mapOf(
    // ── Squat / knee-dominant family ─────────────────────────────────────────
    "goblet_squat"          to "squat",
    "front_squat"           to "squat",
    "sumo_squat"            to "squat",
    "box_squat"             to "squat",
    "wall_sit"              to "squat",
    "jump_squat"            to "squat",
    "hack_squat"            to "leg_press",
    "leg_extension"         to "leg_press",
    "leg_curl"              to "leg_press",
    // ── Single-leg / lunge family ─────────────────────────────────────────────
    "reverse_lunge"         to "lunge",
    "lateral_lunge"         to "lunge",
    "bulgarian_split_squat" to "lunge",
    "step_up"               to "lunge",
    "pistol_squat"          to "lunge",
    "skater_lunge"          to "lunge",
    // ── Deadlift / hinge family ───────────────────────────────────────────────
    "romanian_deadlift"     to "deadlift",
    "sumo_deadlift"         to "deadlift",
    "single_leg_deadlift"   to "deadlift",
    "good_morning"          to "deadlift",
    "nordic_curl"           to "deadlift",
    "glute_bridge"          to "hip_thrust",
    "kettlebell_swing"      to "hip_thrust",
    // ── Horizontal push / chest family ───────────────────────────────────────
    "incline_bench"         to "bench",
    "dumbbell_fly"          to "bench",
    "cable_fly"             to "bench",
    "close_grip_bench"      to "bench",
    "chest_press_machine"   to "bench",
    "diamond_pushup"        to "pushup",
    "wide_pushup"           to "pushup",
    "decline_pushup"        to "pushup",
    "incline_pushup"        to "pushup",
    "pike_pushup"           to "pushup",
    // ── Horizontal pull / row family ──────────────────────────────────────────
    "dumbbell_row"          to "row",
    "cable_row"             to "row",
    "t_bar_row"             to "row",
    "band_row"              to "row",
    "chest_supported_row"   to "row",
    "face_pull"             to "row",
    "inverted_row"          to "row",
    "rowing_machine"        to "row",
    "battle_rope"           to "row",
    // ── Vertical pull / pullup bar family ────────────────────────────────────
    "chin_up"               to "pullup",
    "wide_pullup"           to "pullup",
    "assisted_pullup"       to "pullup",
    "muscle_up"             to "pullup",
    "lat_pulldown"          to "pullup",
    "band_pulldown"         to "pullup",
    "hanging_leg_raise"     to "pullup",
    "toes_to_bar"           to "pullup",
    "l_sit"                 to "pullup",
    // ── Vertical push / shoulder family ──────────────────────────────────────
    "dumbbell_shoulder_press" to "ohp",
    "arnold_press"          to "ohp",
    "lateral_raise"         to "ohp",
    "front_raise"           to "ohp",
    "handstand_pushup"      to "ohp",
    "upright_row"           to "ohp",
    "military_press_machine" to "ohp",
    // ── Biceps family ─────────────────────────────────────────────────────────
    "hammer_curl"           to "curl",
    "incline_curl"          to "curl",
    "concentration_curl"    to "curl",
    "cable_curl"            to "curl",
    "reverse_curl"          to "curl",
    // ── Triceps family ───────────────────────────────────────────────────────
    "skull_crusher"         to "tricep_extension",
    "tricep_pushdown"       to "tricep_extension",
    "tricep_kickback"       to "tricep_extension",
    "overhead_tricep_extension" to "tricep_extension",
    // ── Core family ───────────────────────────────────────────────────────────
    "side_plank"            to "plank",
    "crunch"                to "plank",
    "bicycle_crunch"        to "plank",
    "leg_raise"             to "plank",
    "mountain_climber"      to "plank",
    "russian_twist"         to "plank",
    "dead_bug"              to "plank",
    "hollow_body"           to "plank",
    "v_up"                  to "plank",
    "ab_wheel"              to "plank",
    "cable_crunch"          to "plank",
    // ── Cardio family ─────────────────────────────────────────────────────────
    "sprint"                to "run",
    "burpee"                to "run",
    "jumping_jack"          to "run",
    "high_knees"            to "run",
    "jump_rope"             to "run",
    "box_jump"              to "run",
    "stair_climb"           to "run",
    "skater_jump"           to "run",
    "swim"                  to "run",
    "elliptical"            to "bike",
    // ── Mobility / recovery family ────────────────────────────────────────────
    "stretching"            to "yoga",
    "foam_rolling"          to "yoga",
    "hip_flexor_stretch"    to "yoga",
    "world_greatest_stretch" to "yoga",
    "cat_cow"               to "yoga"
)

fun normalizeCoachTrainerId(raw: String?): String = CoachTrainerIds.normalize(raw)

fun coachAvatarFor(trainerId: String): DrawableResource =
    DefaultCoachVisualProvider.avatar(trainerId)

fun coachExerciseIllustration(
    trainerId: String,
    exerciseId: String,
    fallback: DrawableResource
): DrawableResource =
    DefaultCoachVisualProvider.exerciseIllustration(trainerId, exerciseId, fallback)

private val arturExerciseIllustrations = mapOf(
    "squat" to Res.drawable.coach_artur_squat,
    "leg_press" to Res.drawable.coach_artur_leg_press,
    "lunge" to Res.drawable.coach_artur_lunge,
    "deadlift" to Res.drawable.coach_artur_deadlift,
    "bench" to Res.drawable.coach_artur_bench,
    "pushup" to Res.drawable.coach_artur_pushup,
    "dip" to Res.drawable.coach_artur_dip,
    "row" to Res.drawable.coach_artur_row,
    "pullup" to Res.drawable.coach_artur_pullup,
    "ohp" to Res.drawable.coach_artur_ohp,
    "curl" to Res.drawable.coach_artur_curl,
    "tricep_extension" to Res.drawable.coach_artur_tricep_extension,
    "run" to Res.drawable.coach_artur_run,
    "bike" to Res.drawable.coach_artur_bike,
    "yoga" to Res.drawable.coach_artur_yoga
)

private val miaExerciseIllustrations = mapOf(
    "squat" to Res.drawable.coach_mia_squat,
    "leg_press" to Res.drawable.coach_mia_leg_press,
    "lunge" to Res.drawable.coach_mia_lunge,
    "deadlift" to Res.drawable.coach_mia_deadlift,
    "hip_thrust" to Res.drawable.coach_mia_hip_thrust,
    "bench" to Res.drawable.coach_mia_bench,
    "pushup" to Res.drawable.coach_mia_pushup,
    "dip" to Res.drawable.coach_mia_dip,
    "row" to Res.drawable.coach_mia_row,
    "pullup" to Res.drawable.coach_mia_pullup,
    "ohp" to Res.drawable.coach_mia_ohp,
    "curl" to Res.drawable.coach_mia_curl,
    "tricep_extension" to Res.drawable.coach_mia_tricep_extension,
    "run" to Res.drawable.coach_mia_run,
    "bike" to Res.drawable.coach_mia_bike,
    "yoga" to Res.drawable.coach_mia_yoga
)

private val vtempeExerciseIllustrations = mapOf(
    "squat" to Res.drawable.coach_vtempe_squat,
    "leg_press" to Res.drawable.coach_vtempe_leg_press,
    "lunge" to Res.drawable.coach_vtempe_lunge,
    "deadlift" to Res.drawable.coach_vtempe_deadlift,
    "hip_thrust" to Res.drawable.coach_vtempe_hip_thrust,
    "bench" to Res.drawable.coach_vtempe_bench,
    "pushup" to Res.drawable.coach_vtempe_pushup,
    "dip" to Res.drawable.coach_vtempe_dip,
    "row" to Res.drawable.coach_vtempe_row,
    "pullup" to Res.drawable.coach_vtempe_pullup,
    "ohp" to Res.drawable.coach_vtempe_ohp,
    "curl" to Res.drawable.coach_vtempe_curl,
    "tricep_extension" to Res.drawable.coach_vtempe_tricep_extension,
    "plank" to Res.drawable.coach_vtempe_plank,
    "run" to Res.drawable.coach_vtempe_run,
    "bike" to Res.drawable.coach_vtempe_bike,
    "yoga" to Res.drawable.coach_vtempe_yoga
)
