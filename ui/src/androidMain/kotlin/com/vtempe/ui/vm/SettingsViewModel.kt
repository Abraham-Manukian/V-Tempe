package com.vtempe.ui.vm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vtempe.shared.domain.model.Profile
import com.vtempe.shared.domain.repository.PreferencesRepository
import com.vtempe.shared.domain.repository.ProfileRepository
import com.vtempe.shared.domain.usecase.EnsureCoachData
import com.vtempe.ui.screens.SettingsPresenter
import com.vtempe.ui.screens.SettingsState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class SettingsViewModel(
    private val profileRepository: ProfileRepository,
    private val preferencesRepository: PreferencesRepository,
    private val ensureCoachData: EnsureCoachData
) : ViewModel(), SettingsPresenter {
    private val _state = MutableStateFlow(SettingsState())
    override val state: StateFlow<SettingsState> = _state.asStateFlow()

    init { refresh() }

    override fun refresh() {
        viewModelScope.launch { _state.value = SettingsState(profileRepository.getProfile()) }
    }

    override fun reset(onDone: () -> Unit) {
        viewModelScope.launch {
            _state.value = _state.value.copy(saving = true)
            profileRepository.clearAll()
            _state.value = SettingsState(profile = null, saving = false)
            onDone()
        }
    }

    override fun save(profile: Profile) {
        viewModelScope.launch {
            _state.value = _state.value.copy(saving = true)
            profileRepository.upsertProfile(profile)
            _state.value = SettingsState(profile, saving = false)
        }
    }

    override fun setUnits(units: String) {
        preferencesRepository.setUnits(units)
    }

    override fun setLanguage(tag: String?) {
        preferencesRepository.setLanguageTag(tag)
        if (tag.isNullOrBlank()) {
            androidx.appcompat.app.AppCompatDelegate.setApplicationLocales(androidx.core.os.LocaleListCompat.getEmptyLocaleList())
        } else {
            androidx.appcompat.app.AppCompatDelegate.setApplicationLocales(androidx.core.os.LocaleListCompat.forLanguageTags(tag))
        }
        viewModelScope.launch {
            runCatching { ensureCoachData(weekIndex = 0, force = true) }
        }
    }
}

