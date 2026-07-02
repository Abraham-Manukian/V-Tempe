package com.vtempe.ui.presenter

import com.vtempe.shared.domain.model.AiModelMode
import com.vtempe.shared.domain.model.Profile
import com.vtempe.shared.domain.repository.PreferencesRepository
import com.vtempe.shared.domain.repository.ProfileRepository
import com.vtempe.shared.domain.usecase.EnsureCoachData
import com.vtempe.shared.domain.usecase.ResetCoachData
import com.vtempe.shared.domain.usecase.SyncAnalyticsProfile
import io.github.aakira.napier.Napier
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SettingsState(
    val profile: Profile? = null,
    val saving: Boolean = false,
    val aiModelMode: AiModelMode = AiModelMode.PAID,
    val analyticsConsent: Boolean = false
)

interface SettingsPresenter {
    val state: StateFlow<SettingsState>
    fun refresh()
    fun save(profile: Profile)
    fun reset(onDone: () -> Unit)
    fun setUnits(units: String)
    fun setLanguage(tag: String?)
    fun setAiModelMode(mode: AiModelMode)
    fun setAnalyticsConsent(granted: Boolean)
}

class SettingsPresenterDelegate(
    private val profileRepository: ProfileRepository,
    private val preferencesRepository: PreferencesRepository,
    private val ensureCoachData: EnsureCoachData,
    private val resetCoachData: ResetCoachData,
    private val syncAnalyticsProfile: SyncAnalyticsProfile,
    private val scope: CoroutineScope,
    /** Platform hook: Android calls AppCompatDelegate, iOS is no-op. */
    private val applyLocale: (tag: String?) -> Unit = {}
) : SettingsPresenter {

    private val _state = MutableStateFlow(SettingsState())
    override val state: StateFlow<SettingsState> = _state.asStateFlow()

    init { refresh() }

    override fun refresh() {
        scope.launch {
            val profile = runCatching { profileRepository.getProfile() }.getOrNull()
            val mode = runCatching { preferencesRepository.getAiModelMode() }.getOrDefault(AiModelMode.PAID)
            val consent = runCatching { preferencesRepository.getAnalyticsConsent() }.getOrDefault(false)
            _state.update { it.copy(profile = profile, aiModelMode = mode, analyticsConsent = consent) }
        }
    }

    override fun save(profile: Profile) {
        _state.update { it.copy(saving = true) }
        scope.launch {
            runCatching {
                profileRepository.upsertProfile(profile)
                ensureCoachData(force = true)
                syncAnalyticsProfile(profile) // no-op internally unless consent is already granted
            }.onFailure { Napier.e("Settings save failed", it) }
            _state.update { it.copy(profile = profile, saving = false) }
        }
    }

    override fun reset(onDone: () -> Unit) {
        scope.launch {
            // Full wipe: DB (profile + workouts + nutrition) + AI cache + epoch date.
            // Week counter restarts from zero on the next bootstrap.
            runCatching { resetCoachData() }
                .onFailure { Napier.e("Reset failed", it) }
            _state.value = SettingsState()
            onDone()
        }
    }

    override fun setUnits(units: String) {
        preferencesRepository.setUnits(units)
    }

    override fun setLanguage(tag: String?) {
        preferencesRepository.setLanguageTag(tag)
        applyLocale(tag)
    }

    override fun setAiModelMode(mode: AiModelMode) {
        preferencesRepository.setAiModelMode(mode)
        _state.update { it.copy(aiModelMode = mode) }
    }

    override fun setAnalyticsConsent(granted: Boolean) {
        preferencesRepository.setAnalyticsConsent(granted)
        _state.update { it.copy(analyticsConsent = granted) }
        // Send the bucketed demographic snapshot immediately on opt-in so it's not
        // waiting on the next profile save. Opting out only stops FUTURE syncs —
        // Firebase has no API to retroactively delete already-sent user properties.
        if (granted) {
            _state.value.profile?.let { syncAnalyticsProfile(it) }
        }
    }
}
