package com.vtempe.ui.vm

import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vtempe.shared.domain.repository.AnalyticsRepository
import com.vtempe.shared.domain.repository.LanguagePreferences
import com.vtempe.shared.domain.repository.ProfileRepository
import com.vtempe.shared.domain.usecase.BootstrapCoachData
import com.vtempe.ui.presenter.OnboardingPresenter
import com.vtempe.ui.presenter.OnboardingPresenterDelegate
import com.vtempe.ui.presenter.OnboardingState
import kotlinx.coroutines.flow.StateFlow

class OnboardingViewModel(
    profileRepository: ProfileRepository,
    bootstrapCoachData: BootstrapCoachData,
    languagePrefs: LanguagePreferences,
    analytics: AnalyticsRepository
) : ViewModel(), OnboardingPresenter {

    private val delegate = OnboardingPresenterDelegate(
        profileRepository = profileRepository,
        bootstrapCoachData = bootstrapCoachData,
        languagePrefs = languagePrefs,
        scope = viewModelScope,
        applyLocale = { tag ->
            if (tag == null) {
                AppCompatDelegate.setApplicationLocales(LocaleListCompat.getEmptyLocaleList())
            } else {
                AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(tag))
            }
        },
        analytics = analytics
    )

    override val state: StateFlow<OnboardingState> get() = delegate.state
    override fun update(transform: (OnboardingState) -> OnboardingState) = delegate.update(transform)
    override fun toggleEquipment(option: String) = delegate.toggleEquipment(option)
    override fun setCustomEquipment(value: String) = delegate.setCustomEquipment(value)
    override fun setLanguage(tag: String) = delegate.setLanguage(tag)
    override fun nextStep() = delegate.nextStep()
    override fun prevStep() = delegate.prevStep()
    override fun save(onSuccess: () -> Unit) = delegate.save(onSuccess)
}
