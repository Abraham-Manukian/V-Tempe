package com.vtempe.ui.presenter

import com.vtempe.shared.domain.model.CoachTrainerIds
import com.vtempe.shared.domain.model.Goal
import com.vtempe.shared.domain.model.LifestyleActivity
import com.vtempe.shared.domain.model.Profile
import com.vtempe.shared.domain.model.Sex
import com.vtempe.shared.domain.model.SplitPreference
import com.vtempe.shared.domain.model.TrainingFocus
import com.vtempe.shared.domain.repository.LanguagePreferences
import com.vtempe.shared.domain.repository.ProfileRepository
import com.vtempe.shared.domain.usecase.BootstrapCoachData
import io.github.aakira.napier.Napier
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.random.Random

const val ONBOARDING_TOTAL_STEPS = 14
const val TRAINING_MODE_GYM = "gym"
const val TRAINING_MODE_HOME = "home"
const val TRAINING_MODE_OUTDOOR = "outdoor"
const val TRAINING_MODE_MIXED = "mixed"
const val TRAINING_FOCUS_STRENGTH = "STRENGTH"
const val TRAINING_FOCUS_HYPERTROPHY = "HYPERTROPHY"
const val TRAINING_FOCUS_GENERAL = "GENERAL"
const val TRAINING_FOCUS_FAT_LOSS = "FAT_LOSS"
internal const val EQUIPMENT_NOTE_MAX_CHARS = 200

data class OnboardingState(
    val age: String = "28",
    val sex: Sex = Sex.MALE,
    val heightCm: String = "178",
    val weightKg: String = "78",
    val goal: Goal = Goal.MAINTAIN,
    val experienceLevel: Int = 3,
    val dietaryPreferences: String = "",
    val allergies: String = "",
    /** Comma-separated injuries / health notes entered by user on step 7. */
    val injuries: String = "",
    /** 1 = budget / student, 2 = medium, 3 = premium. Selected on step 8. */
    val budgetLevel: Int = 2,
    val trainingMode: String = TRAINING_MODE_GYM,
    val trainingFocus: String = TRAINING_FOCUS_HYPERTROPHY,
    val sessionDurationMins: Int = 60,
    val coachTrainerId: String = CoachTrainerIds.DEFAULT,
    val selectedEquipment: Set<String> = emptySet(),
    val customEquipment: String = "",
    val lifestyleActivity: LifestyleActivity = LifestyleActivity.SEDENTARY,
    val splitPreference: SplitPreference = SplitPreference.AUTO,
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

class OnboardingPresenterDelegate(
    private val profileRepository: ProfileRepository,
    private val bootstrapCoachData: BootstrapCoachData,
    private val languagePrefs: LanguagePreferences,
    private val scope: CoroutineScope,
    /** Platform hook: Android calls AppCompatDelegate, iOS is no-op. */
    private val applyLocale: (tag: String?) -> Unit = {}
) : OnboardingPresenter {

    private val _state = MutableStateFlow(OnboardingState())
    override val state: StateFlow<OnboardingState> = _state.asStateFlow()

    override fun update(transform: (OnboardingState) -> OnboardingState) {
        _state.update(transform)
    }

    override fun toggleEquipment(option: String) {
        _state.update { s ->
            val current = s.selectedEquipment
            s.copy(selectedEquipment = if (option in current) current - option else current + option)
        }
    }

    override fun setCustomEquipment(value: String) {
        _state.update { it.copy(customEquipment = value.take(EQUIPMENT_NOTE_MAX_CHARS)) }
    }

    override fun setLanguage(tag: String) {
        val resolved = if (tag == "system") null else tag
        languagePrefs.setLanguageTag(resolved)
        applyLocale(resolved)
        _state.update { it.copy(languageTag = tag) }
    }

    override fun nextStep() {
        _state.update { s ->
            if (s.currentStep < ONBOARDING_TOTAL_STEPS - 1) s.copy(currentStep = s.currentStep + 1) else s
        }
    }

    override fun prevStep() {
        _state.update { s ->
            if (s.currentStep > 0) s.copy(currentStep = s.currentStep - 1) else s
        }
    }

    override fun save(onSuccess: () -> Unit) {
        val s = _state.value
        _state.update { it.copy(saving = true, error = null) }
        scope.launch {
            runCatching {
                val profile = Profile(
                    id = "user_${Random.nextInt(100_000)}",
                    age = s.age.toIntOrNull() ?: 28,
                    sex = s.sex,
                    heightCm = s.heightCm.toIntOrNull() ?: 178,
                    weightKg = s.weightKg.toDoubleOrNull() ?: 78.0,
                    goal = s.goal,
                    experienceLevel = s.experienceLevel,
                    dietaryPreferences = s.dietaryPreferences.split(",").map { it.trim() }.filter { it.isNotEmpty() },
                    allergies = s.allergies.split(",").map { it.trim() }.filter { it.isNotEmpty() },
                    constraints = com.vtempe.shared.domain.model.Constraints(
                        injuries = s.injuries.split(",").map { it.trim() }.filter { it.isNotEmpty() }
                    ),
                    budgetLevel = s.budgetLevel,
                    weeklySchedule = s.days,
                    lifestyleActivity = s.lifestyleActivity,
                    trainingMode = s.trainingMode,
                    trainingFocus = runCatching { TrainingFocus.valueOf(s.trainingFocus) }.getOrDefault(TrainingFocus.GENERAL),
                    sessionDurationMins = s.sessionDurationMins,
                    splitPreference = s.splitPreference,
                    coachTrainerId = s.coachTrainerId,
                    equipment = com.vtempe.shared.domain.model.Equipment(
                        items = s.selectedEquipment.toList() +
                                s.customEquipment.split(",").map { it.trim() }.filter { it.isNotEmpty() }
                    )
                )
                profileRepository.upsertProfile(profile)
                bootstrapCoachData()
            }.onSuccess {
                _state.update { it.copy(saving = false) }
                onSuccess()
            }.onFailure { e ->
                Napier.e("Onboarding save failed", e)
                _state.update { it.copy(saving = false, error = e.message) }
            }
        }
    }
}
