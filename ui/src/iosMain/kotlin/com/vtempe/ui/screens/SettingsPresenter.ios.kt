package com.vtempe.ui.screens

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import com.vtempe.shared.data.di.KoinProvider
import com.vtempe.shared.domain.repository.PreferencesRepository
import com.vtempe.shared.domain.repository.ProfileRepository
import com.vtempe.shared.domain.usecase.EnsureCoachData
import com.vtempe.ui.presenter.SettingsPresenter
import com.vtempe.ui.presenter.SettingsPresenterDelegate
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

private class IosSettingsPresenter(
    profileRepository: ProfileRepository,
    preferencesRepository: PreferencesRepository,
    ensureCoachData: EnsureCoachData
) : SettingsPresenter {
    private val job = SupervisorJob()
    private val scope = CoroutineScope(Dispatchers.Main + job)
    // iOS: applyLocale is a no-op — the OS handles locale at the SwiftUI layer
    private val delegate = SettingsPresenterDelegate(
        profileRepository = profileRepository,
        preferencesRepository = preferencesRepository,
        ensureCoachData = ensureCoachData,
        scope = scope
    )
    override val state get() = delegate.state
    override fun refresh() = delegate.refresh()
    override fun save(profile: com.vtempe.shared.domain.model.Profile) = delegate.save(profile)
    override fun reset(onDone: () -> Unit) = delegate.reset(onDone)
    override fun setUnits(units: String) = delegate.setUnits(units)
    override fun setLanguage(tag: String?) = delegate.setLanguage(tag)
    override fun setAiModelMode(mode: com.vtempe.shared.domain.model.AiModelMode) = delegate.setAiModelMode(mode)
    fun close() = job.cancel()
}

@Composable
actual fun rememberSettingsPresenter(): SettingsPresenter {
    val presenter = remember {
        val koin = requireNotNull(KoinProvider.koin) { "Koin is not started" }
        IosSettingsPresenter(
            profileRepository = koin.get(),
            preferencesRepository = koin.get(),
            ensureCoachData = koin.get()
        )
    }
    DisposableEffect(Unit) { onDispose { presenter.close() } }
    return presenter
}
