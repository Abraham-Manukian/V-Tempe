package com.vtempe.ui.screens.profile

import com.vtempe.shared.domain.model.CoachTrainerIds
import com.vtempe.shared.domain.model.Goal
import com.vtempe.shared.domain.model.LifestyleActivity
import com.vtempe.shared.domain.model.Profile
import com.vtempe.shared.domain.model.ProfileBounds
import com.vtempe.shared.domain.model.SplitPreference
import com.vtempe.shared.domain.model.TrainingFocus
import com.vtempe.ui.presenter.TRAINING_MODE_GYM
import androidx.compose.runtime.saveable.Saver
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class EditProfileState(
    val age: String = "",
    val heightCm: String = "",
    val weightKg: String = "",
    val goal: Goal = Goal.MAINTAIN,
    val trainingMode: String = TRAINING_MODE_GYM,
    val trainingFocus: TrainingFocus = TrainingFocus.GENERAL,
    val splitPreference: SplitPreference = SplitPreference.AUTO,
    val experienceLevel: Int = 3,
    val lifestyleActivity: LifestyleActivity = LifestyleActivity.SEDENTARY,
    val sessionDurationMins: Int = 60,
    val injuries: String = "",
    val dietaryPrefs: String = "",
    val allergies: String = "",
    val coachTrainerId: String = CoachTrainerIds.DEFAULT,
) {
    /** Measurements that are missing or outside [ProfileBounds] — shown under each field (VT-AUD-035). */
    val invalidFields: Set<ProfileBounds.Field>
        get() = ProfileBounds.invalidFields(age.toIntOrNull(), heightCm.toIntOrNull(), ProfileBounds.parseWeight(weightKg))

    val isValid: Boolean
        get() = invalidFields.isEmpty()

    fun applyTo(base: Profile): Profile = base.copy(
        age = age.toInt(),
        heightCm = heightCm.toInt(),
        weightKg = ProfileBounds.parseWeight(weightKg) ?: base.weightKg,
        goal = goal,
        trainingMode = trainingMode,
        trainingFocus = trainingFocus,
        splitPreference = splitPreference,
        experienceLevel = experienceLevel,
        lifestyleActivity = lifestyleActivity,
        sessionDurationMins = sessionDurationMins,
        constraints = base.constraints.copy(
            injuries = injuries.splitTrimmed()
        ),
        dietaryPreferences = dietaryPrefs.splitTrimmed(),
        allergies = allergies.splitTrimmed(),
        coachTrainerId = coachTrainerId,
    )
}

/** Keeps an unsaved draft across Activity recreation (VT-AUD-065). */
internal val EditProfileDraftSaver: Saver<EditProfileState, String> = Saver(
    save = { draftJson.encodeToString(EditProfileState.serializer(), it) },
    restore = { runCatching { draftJson.decodeFromString(EditProfileState.serializer(), it) }.getOrNull() },
)

private val draftJson = Json { ignoreUnknownKeys = true }

fun Profile.toEditState() = EditProfileState(
    age = age.toString(),
    heightCm = heightCm.toString(),
    weightKg = weightKg.toString(),
    goal = goal,
    trainingMode = trainingMode,
    trainingFocus = trainingFocus,
    splitPreference = splitPreference,
    experienceLevel = experienceLevel.coerceIn(1, 5),
    lifestyleActivity = lifestyleActivity,
    sessionDurationMins = sessionDurationMins,
    injuries = constraints.injuries.joinToString(", "),
    dietaryPrefs = dietaryPreferences.joinToString(", "),
    allergies = allergies.joinToString(", "),
    coachTrainerId = coachTrainerId,
)

private fun String.splitTrimmed() =
    split(",").map { it.trim() }.filter { it.isNotEmpty() }
