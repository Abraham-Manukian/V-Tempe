package com.vtempe.ui.screens

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import com.vtempe.shared.domain.model.AiModelMode
import com.vtempe.shared.domain.model.Profile
import com.vtempe.shared.domain.repository.PreferencesRepository
import com.vtempe.shared.domain.repository.ProfileRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import com.vtempe.shared.data.di.KoinProvider

private class IosSettingsPresenter(
    private val profileRepository: ProfileRepository,
    private val preferencesRepository: PreferencesRepository
) : SettingsPresenter {
    private val job = SupervisorJob()
    private val scope = CoroutineScope(Dispatchers.Main + job)

    private val mutableState = MutableStateFlow(SettingsState())
    override val state: StateFlow<SettingsState> = mutableState

    init {
        refresh()
    }

    override fun refresh() {
        scope.launch {
            mutableState.value = SettingsState(
                profile = profileRepository.getProfile(),
                aiModelMode = preferencesRepository.getAiModelMode()
            )
        }
    }

    override fun reset(onDone: () -> Unit) {
        scope.launch {
            mutableState.value = mutableState.value.copy(saving = true)
            profileRepository.clearAll()
            mutableState.value = SettingsState(
                profile = null,
                saving = false,
                aiModelMode = preferencesRepository.getAiModelMode()
            )
            onDone()
        }
    }

    override fun save(profile: Profile) {
        scope.launch {
            mutableState.value = mutableState.value.copy(saving = true)
            profileRepository.upsertProfile(profile)
            mutableState.value = SettingsState(
                profile = profile,
                saving = false,
                aiModelMode = preferencesRepository.getAiModelMode()
            )
        }
    }

    override fun setUnits(units: String) {
        preferencesRepository.setUnits(units)
    }

    override fun setLanguage(tag: String?) {
        preferencesRepository.setLanguageTag(tag)
    }

    override fun setAiModelMode(mode: AiModelMode) {
        preferencesRepository.setAiModelMode(mode)
        mutableState.value = mutableState.value.copy(aiModelMode = mode)
    }

    fun close() {
        job.cancel()
    }
}

@Composable
actual fun rememberSettingsPresenter(): SettingsPresenter {
    val presenter = remember {
        val koin = requireNotNull(KoinProvider.koin) { "Koin is not started" }
        IosSettingsPresenter(
            profileRepository = koin.get<ProfileRepository>(),
            preferencesRepository = koin.get<PreferencesRepository>()
        )
    }
    DisposableEffect(Unit) { onDispose { presenter.close() } }
    return presenter
}

