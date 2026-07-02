package com.vtempe.ui.screens

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import com.vtempe.shared.data.di.KoinProvider
import com.vtempe.shared.domain.repository.AnalyticsRepository
import com.vtempe.shared.domain.repository.LanguagePreferences
import com.vtempe.shared.domain.repository.ProfileRepository
import com.vtempe.shared.domain.usecase.BootstrapCoachData
import com.vtempe.ui.presenter.OnboardingPresenter
import com.vtempe.ui.presenter.OnboardingPresenterDelegate
import com.vtempe.ui.presenter.OnboardingState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

private class IosOnboardingPresenter(
    profileRepository: ProfileRepository,
    bootstrapCoachData: BootstrapCoachData,
    languagePrefs: LanguagePreferences,
    analytics: AnalyticsRepository
) : OnboardingPresenter {
    private val job = SupervisorJob()
    private val scope = CoroutineScope(Dispatchers.Main + job)
    // iOS: applyLocale is a no-op — the OS handles locale at the SwiftUI layer
    private val delegate = OnboardingPresenterDelegate(
        profileRepository = profileRepository,
        bootstrapCoachData = bootstrapCoachData,
        languagePrefs = languagePrefs,
        scope = scope,
        analytics = analytics
    )
    override val state get() = delegate.state
    override fun update(transform: (OnboardingState) -> OnboardingState) = delegate.update(transform)
    override fun toggleEquipment(option: String) = delegate.toggleEquipment(option)
    override fun setCustomEquipment(value: String) = delegate.setCustomEquipment(value)
    override fun setLanguage(tag: String) = delegate.setLanguage(tag)
    override fun nextStep() = delegate.nextStep()
    override fun prevStep() = delegate.prevStep()
    override fun save(onSuccess: () -> Unit) = delegate.save(onSuccess)
    fun close() = job.cancel()
}

@Composable
actual fun rememberOnboardingPresenter(): OnboardingPresenter {
    val presenter = remember {
        val koin = requireNotNull(KoinProvider.koin) { "Koin is not started" }
        IosOnboardingPresenter(
            profileRepository = koin.get(),
            bootstrapCoachData = koin.get(),
            languagePrefs = koin.get(),
            analytics = koin.get()
        )
    }
    DisposableEffect(Unit) { onDispose { presenter.close() } }
    return presenter
}
