package com.vtempe.ui.screens

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import com.vtempe.shared.domain.model.Constraints
import com.vtempe.shared.domain.model.Equipment
import com.vtempe.shared.domain.model.Profile
import com.vtempe.shared.domain.repository.PreferencesRepository
import com.vtempe.shared.domain.repository.ProfileRepository
import com.vtempe.shared.domain.usecase.BootstrapCoachData
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import com.vtempe.shared.data.di.KoinProvider

private class IosOnboardingPresenter(
    private val profileRepository: ProfileRepository,
    private val bootstrapCoachData: BootstrapCoachData,
    private val preferencesRepository: PreferencesRepository,
) : OnboardingPresenter {
    private val job = SupervisorJob()
    private val scope = CoroutineScope(Dispatchers.Main + job)

    private val mutableState = MutableStateFlow(OnboardingState())
    override val state: StateFlow<OnboardingState> = mutableState

    private val lastStepIndex = ONBOARDING_TOTAL_STEPS - 1

    init {
        val tag = preferencesRepository.getLanguageTag() ?: "system"
        mutableState.value = mutableState.value.copy(languageTag = tag)
    }

    override fun update(transform: (OnboardingState) -> OnboardingState) {
        mutableState.value = transform(mutableState.value)
    }

    override fun toggleEquipment(option: String) {
        update { st ->
            val newSet = if (st.selectedEquipment.contains(option)) st.selectedEquipment - option else st.selectedEquipment + option
            st.copy(selectedEquipment = newSet)
        }
    }

    override fun setCustomEquipment(value: String) {
        update { it.copy(customEquipment = value) }
    }

    override fun setLanguage(tag: String) {
        update { it.copy(languageTag = tag) }
        if (tag == "system") {
            preferencesRepository.setLanguageTag(null)
        } else {
            preferencesRepository.setLanguageTag(tag)
        }
    }

    override fun nextStep() {
        update { st -> st.copy(currentStep = (st.currentStep + 1).coerceAtMost(lastStepIndex)) }
    }

    override fun prevStep() {
        update { st -> st.copy(currentStep = (st.currentStep - 1).coerceAtLeast(0)) }
    }

    override fun save(onSuccess: () -> Unit) {
        val s = mutableState.value
        val age = s.age.toIntOrNull()
        val h = s.heightCm.toIntOrNull()
        val w = s.weightKg.toDoubleOrNull()
        if (age == null || h == null || w == null) {
            mutableState.value = s.copy(error = "invalid")
            return
        }
        scope.launch {
            mutableState.value = s.copy(saving = true, error = null)
            val manualEquipment =
                s.customEquipment.split(',', ';', '\n').map { it.trim() }.filter { it.isNotEmpty() }
            val allEquipment =
                (s.selectedEquipment + manualEquipment).map { it.trim() }.filter { it.isNotEmpty() }
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
            setLanguage(s.languageTag)
            profileRepository.upsertProfile(profile)
            runCatching { bootstrapCoachData() }
            mutableState.value = s.copy(saving = false)
            onSuccess()
        }
    }

    fun close() {
        job.cancel()
    }
}

@Composable
actual fun rememberOnboardingPresenter(): OnboardingPresenter {
    val presenter = remember {
        val koin = requireNotNull(KoinProvider.koin) { "Koin is not started" }
        IosOnboardingPresenter(
            profileRepository = koin.get<ProfileRepository>(),
            bootstrapCoachData = koin.get<BootstrapCoachData>(),
            preferencesRepository = koin.get<PreferencesRepository>()
        )
    }
    DisposableEffect(Unit) { onDispose { presenter.close() } }
    return presenter
}

