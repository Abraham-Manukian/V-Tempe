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
        val normalizedExercise = exerciseId.lowercase().replace('-', '_')
        return when (normalizedTrainer) {
            CoachTrainerIds.ARTUR -> arturExerciseIllustrations[normalizedExercise]
            CoachTrainerIds.VTEMPE -> vtempeExerciseIllustrations[normalizedExercise]
            else -> miaExerciseIllustrations[normalizedExercise]
        } ?: fallback
    }
}

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
