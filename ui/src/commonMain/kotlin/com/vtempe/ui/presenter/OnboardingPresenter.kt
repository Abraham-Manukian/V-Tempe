package com.vtempe.ui.presenter

import com.vtempe.shared.domain.consent.HealthDataConsentManager
import com.vtempe.shared.domain.model.CoachTrainerIds
import com.vtempe.shared.domain.model.Goal
import com.vtempe.shared.domain.model.LifestyleActivity
import com.vtempe.shared.domain.model.Profile
import com.vtempe.shared.domain.model.Sex
import com.vtempe.shared.domain.model.SplitPreference
import com.vtempe.shared.domain.model.TrainingFocus
import com.vtempe.shared.domain.repository.AnalyticsConsentPreferences
import com.vtempe.shared.domain.repository.AnalyticsRepository
import com.vtempe.shared.domain.repository.AnalyticsEvents
import com.vtempe.shared.domain.repository.LanguagePreferences
import com.vtempe.shared.domain.repository.ProfileRepository
import com.vtempe.shared.domain.usecase.BootstrapCoachData
import com.vtempe.shared.domain.usecase.SyncAnalyticsProfile
import io.github.aakira.napier.Napier
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CancellationException
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

enum class OnboardingError { INVALID_INPUT, SAVE_FAILED, GENERATION_FAILED, CONSENT_REQUIRED }

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
    /** Opt-in for bucketed demographic analytics — asked on the last onboarding step. */
    val analyticsConsent: Boolean = false,
    /** Required, separate consent to health data processing and its transfer abroad for the AI
     *  coach (152-FZ art. 10/12). Never pre-checked; onboarding cannot finish without it. */
    val healthDataConsent: Boolean = false,
    val currentStep: Int = 0,
    val saving: Boolean = false,
    val savingStep: Int = 0,  // 0=profile, 1=generating plan
    val error: OnboardingError? = null
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
    private val applyLocale: (tag: String?) -> Unit = {},
    private val analytics: AnalyticsRepository,
    private val analyticsConsentPreferences: AnalyticsConsentPreferences,
    private val syncAnalyticsProfile: SyncAnalyticsProfile,
    private val healthDataConsent: HealthDataConsentManager
) : OnboardingPresenter {

    private val _state = MutableStateFlow(OnboardingState(languageTag = languagePrefs.getLanguageTag() ?: "system"))
    override val state: StateFlow<OnboardingState> = _state.asStateFlow()
    private val newProfileId = "user_${Random.nextLong().toString(16)}_${Random.nextLong().toString(16)}"

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
        if (_state.value.saving) return
        if (_state.value.currentStep == 1 && !hasValidMeasurements(_state.value)) {
            _state.update { it.copy(error = OnboardingError.INVALID_INPUT) }
            return
        }
        _state.update { s ->
            if (s.currentStep < ONBOARDING_TOTAL_STEPS - 1) s.copy(currentStep = s.currentStep + 1, error = null) else s
        }
    }

    override fun prevStep() {
        if (_state.value.saving) return
        _state.update { s ->
            if (s.currentStep > 0) s.copy(currentStep = s.currentStep - 1, error = null) else s
        }
    }

    override fun save(onSuccess: () -> Unit) {
        val s = _state.value
        if (s.saving) return
        if (!hasValidMeasurements(s)) {
            _state.update { it.copy(currentStep = 1, error = OnboardingError.INVALID_INPUT) }
            return
        }
        if (!s.healthDataConsent) {
            _state.update { it.copy(error = OnboardingError.CONSENT_REQUIRED) }
            return
        }
        // Recorded before anything is sent: the AI gate checks it on the bootstrap request below.
        healthDataConsent.grant()
        _state.update { it.copy(saving = true, savingStep = 0, error = null) }
        scope.launch {
            try {
                val existingProfile = profileRepository.getProfile()
                val profile = Profile(
                    id = existingProfile?.id ?: newProfileId,
                    age = s.age.toInt(),
                    sex = s.sex,
                    heightCm = s.heightCm.toInt(),
                    weightKg = s.weightKg.toDouble(),
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
                analyticsConsentPreferences.setAnalyticsConsent(s.analyticsConsent)
                syncAnalyticsProfile(profile) // no-op internally unless consent was just granted
                _state.update { it.copy(savingStep = 1) }
                if (!bootstrapCoachData(force = existingProfile != null && existingProfile != profile)) {
                    _state.update { it.copy(error = OnboardingError.GENERATION_FAILED) }
                    return@launch
                }
                analytics.logEvent(AnalyticsEvents.PLAN_GENERATED)
                _state.update { it.copy(saving = false) }
                analytics.logEvent(AnalyticsEvents.ONBOARDING_COMPLETE)
                onSuccess()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                // Backend/DB exception messages can contain profile data; keep UI and logs safe.
                Napier.w("Onboarding failed at stage ${_state.value.savingStep}: ${error::class.simpleName}")
                _state.update {
                    it.copy(error = if (it.savingStep == 0) OnboardingError.SAVE_FAILED else OnboardingError.GENERATION_FAILED)
                }
            } finally {
                _state.update { it.copy(saving = false) }
            }
        }
    }

    private fun hasValidMeasurements(state: OnboardingState): Boolean {
        val age = state.age.toIntOrNull() ?: return false
        val height = state.heightCm.toIntOrNull() ?: return false
        val weight = state.weightKg.toDoubleOrNull() ?: return false
        return age > 0 && height > 0 && weight.isFinite() && weight > 0
    }
}
