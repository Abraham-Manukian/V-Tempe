package com.vtempe.ui.vm

import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vtempe.shared.domain.model.AiModelMode
import com.vtempe.shared.domain.model.Profile
import com.vtempe.shared.domain.repository.PreferencesRepository
import com.vtempe.shared.domain.repository.ProfileRepository
import com.vtempe.shared.domain.usecase.EnsureCoachData
import com.vtempe.shared.domain.usecase.ResetCoachData
import com.vtempe.shared.domain.usecase.SyncAnalyticsProfile
import com.vtempe.ui.presenter.SettingsPresenter
import com.vtempe.ui.presenter.SettingsPresenterDelegate
import com.vtempe.ui.presenter.SettingsState
import kotlinx.coroutines.flow.StateFlow

class SettingsViewModel(
    profileRepository: ProfileRepository,
    preferencesRepository: PreferencesRepository,
    ensureCoachData: EnsureCoachData,
    resetCoachData: ResetCoachData,
    syncAnalyticsProfile: SyncAnalyticsProfile,
) : ViewModel(), SettingsPresenter {

    private val delegate = SettingsPresenterDelegate(
        profileRepository = profileRepository,
        preferencesRepository = preferencesRepository,
        ensureCoachData = ensureCoachData,
        resetCoachData = resetCoachData,
        syncAnalyticsProfile = syncAnalyticsProfile,
        scope = viewModelScope,
        applyLocale = { tag ->
            if (tag.isNullOrBlank()) {
                AppCompatDelegate.setApplicationLocales(LocaleListCompat.getEmptyLocaleList())
            } else {
                AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(tag))
            }
        }
    )

    override val state: StateFlow<SettingsState> get() = delegate.state
    override fun refresh() = delegate.refresh()
    override fun save(profile: Profile) = delegate.save(profile)
    override fun reset(onDone: () -> Unit) = delegate.reset(onDone)
    override fun setUnits(units: String) = delegate.setUnits(units)
    override fun setLanguage(tag: String?) = delegate.setLanguage(tag)
    override fun setAiModelMode(mode: AiModelMode) = delegate.setAiModelMode(mode)
    override fun setAnalyticsConsent(granted: Boolean) = delegate.setAnalyticsConsent(granted)
}
