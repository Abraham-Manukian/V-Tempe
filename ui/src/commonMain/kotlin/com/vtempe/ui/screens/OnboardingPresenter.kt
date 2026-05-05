package com.vtempe.ui.screens

import androidx.compose.runtime.Composable
import com.vtempe.shared.domain.model.CoachTrainerIds
import com.vtempe.shared.domain.model.Goal
import com.vtempe.shared.domain.model.Sex
import kotlinx.coroutines.flow.StateFlow

const val ONBOARDING_TOTAL_STEPS = 8
const val TRAINING_MODE_GYM = "gym"
const val TRAINING_MODE_HOME = "home"
const val TRAINING_MODE_OUTDOOR = "outdoor"
const val TRAINING_MODE_MIXED = "mixed"

data class OnboardingState(
    val age: String = "28",
    val sex: Sex = Sex.MALE,
    val heightCm: String = "178",
    val weightKg: String = "78",
    val goal: Goal = Goal.MAINTAIN,
    val experienceLevel: Int = 3,
    val dietaryPreferences: String = "",
    val allergies: String = "",
    val trainingMode: String = TRAINING_MODE_GYM,
    val coachTrainerId: String = CoachTrainerIds.DEFAULT,
    val selectedEquipment: Set<String> = emptySet(),
    val customEquipment: String = "",
    val days: Map<String, Boolean> = mapOf(
        "Mon" to true,
        "Tue" to true,
        "Wed" to false,
        "Thu" to true,
        "Fri" to false,
        "Sat" to false,
        "Sun" to false
    ),
    val languageTag: String = "system",
    val currentStep: Int = 0,
    val saving: Boolean = false,
    val error: String? = null
)

interface OnboardingPresenter {
    val state: StateFlow<OnboardingState>
    fun update(transform: (OnboardingState) -> OnboardingState)
    fun toggleEquipment(option: String)
    fun setCustomEquipment(value: String)
    fun setLanguage(tag: String)
    fun nextStep()
    fun prevStep()
    fun save(onSuccess: () -> Unit)
}

@Composable
expect fun rememberOnboardingPresenter(): OnboardingPresenter

