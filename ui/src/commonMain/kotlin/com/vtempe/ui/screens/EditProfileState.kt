package com.vtempe.ui.screens

import com.vtempe.shared.domain.model.CoachTrainerIds
import com.vtempe.shared.domain.model.Goal
import com.vtempe.shared.domain.model.LifestyleActivity
import com.vtempe.shared.domain.model.Profile
import com.vtempe.shared.domain.model.SplitPreference
import com.vtempe.shared.domain.model.TrainingFocus
import com.vtempe.ui.presenter.TRAINING_MODE_GYM

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
    val isValid: Boolean
        get() = age.toIntOrNull() != null
            && heightCm.toIntOrNull() != null
            && weightKg.toDoubleOrNull() != null

    fun applyTo(base: Profile): Profile = base.copy(
        age = age.toInt(),
        heightCm = heightCm.toInt(),
        weightKg = weightKg.toDouble(),
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
