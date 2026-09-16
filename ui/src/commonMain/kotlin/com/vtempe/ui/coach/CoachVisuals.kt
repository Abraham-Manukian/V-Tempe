package com.vtempe.ui.coach

import com.vtempe.shared.domain.model.CoachTrainerIds
import com.vtempe.ui.*
import com.vtempe.ui.Res
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
 * Fallback aliases: maps an exercise ID to another when no dedicated photo exists.
 * Now that all 107 exercises have coach-specific photos, this map is intentionally
 * empty. Keep it here — if a new exercise is added to the catalog without a photo,
 * add an entry here pointing to the closest visual match.
 */
private val exerciseVisualAlias: Map<String, String> = mapOf(
    // Add entries here only for exercises without a dedicated photo:
    // "new_exercise" to "visually_similar_existing_exercise",
    "military_press_machine" to "ohp",
    "overhead_tricep_extension" to "tricep_extension",
    "chest_press_machine" to "bench",
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
