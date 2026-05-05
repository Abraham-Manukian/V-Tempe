package com.vtempe.ui.vm

import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vtempe.shared.domain.model.Constraints
import com.vtempe.shared.domain.model.Equipment
import com.vtempe.shared.domain.model.Goal
import com.vtempe.shared.domain.model.Profile
import com.vtempe.shared.domain.model.Sex
import com.vtempe.shared.domain.repository.PreferencesRepository
import com.vtempe.shared.domain.repository.ProfileRepository
import com.vtempe.shared.domain.usecase.BootstrapCoachData
import com.vtempe.ui.screens.ONBOARDING_TOTAL_STEPS
import com.vtempe.ui.screens.OnboardingPresenter
import com.vtempe.ui.screens.OnboardingState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class OnboardingViewModel(
    private val profileRepository: ProfileRepository,
    private val bootstrapCoachData: BootstrapCoachData,
    private val preferencesRepository: PreferencesRepository,
) : ViewModel(), OnboardingPresenter {
    private val _state = MutableStateFlow(OnboardingState())
    override val state: StateFlow<OnboardingState> = _state.asStateFlow()

    private val lastStepIndex = ONBOARDING_TOTAL_STEPS - 1

    init {
        val tag = preferencesRepository.getLanguageTag() ?: "system"
        _state.value = _state.value.copy(languageTag = tag)
    }

    override fun update(block: (OnboardingState) -> OnboardingState) { _state.value = block(_state.value) }

    override fun toggleEquipment(option: String) {
        update { st ->
            val newSet = if (st.selectedEquipment.contains(option)) st.selectedEquipment - option else st.selectedEquipment + option
            st.copy(selectedEquipment = newSet)
        }
    }

    override fun setCustomEquipment(value: String) {
        update { it.copy(customEquipment = value.take(200)) }
    }

    override fun nextStep() {
        update { st -> st.copy(currentStep = (st.currentStep + 1).coerceAtMost(lastStepIndex)) }
    }

    override fun prevStep() {
        update { st -> st.copy(currentStep = (st.currentStep - 1).coerceAtLeast(0)) }
    }

    override fun setLanguage(tag: String) {
        update { it.copy(languageTag = tag) }
        if (tag == "system") {
            preferencesRepository.setLanguageTag(null)
            AppCompatDelegate.setApplicationLocales(LocaleListCompat.getEmptyLocaleList())
        } else {
            preferencesRepository.setLanguageTag(tag)
            AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(tag))
        }
    }

    override fun save(onSuccess: () -> Unit) {
        val s = _state.value
        val age = s.age.toIntOrNull()
        val h = s.heightCm.toIntOrNull()
        val w = s.weightKg.toDoubleOrNull()
        if (age == null || h == null || w == null) {
            _state.value = s.copy(error = "invalid")
            return
        }
        viewModelScope.launch {
            _state.value = s.copy(saving = true, error = null)
            val manualEquipment = s.customEquipment.split(',', ';', '\n').map { it.trim() }.filter { it.isNotEmpty() }
            val allEquipment = (s.selectedEquipment + manualEquipment).map { it.trim() }.filter { it.isNotEmpty() }
            val profile = Profile(
                id = "local",
                age = age,
                sex = s.sex,
                heightCm = h,
                weightKg = w,
                goal = s.goal,
                experienceLevel = s.experienceLevel,
                constraints = Constraints(),
                equipment = Equipment(items = allEquipment),
                dietaryPreferences = s.dietaryPreferences.split(',', ';', '\n').map { it.trim() }.filter { it.isNotEmpty() },
                allergies = s.allergies.split(',', ';', '\n').map { it.trim() }.filter { it.isNotEmpty() },
                weeklySchedule = s.days,
                budgetLevel = 2,
                trainingMode = s.trainingMode,
                coachTrainerId = s.coachTrainerId
            )
            // ensure language preference persisted even if user didn't tap chips again
            setLanguage(s.languageTag)
            profileRepository.upsertProfile(profile)
            runCatching { bootstrapCoachData() }.onFailure { it.printStackTrace() }
            _state.value = s.copy(saving = false)
            onSuccess()
        }
    }
}

