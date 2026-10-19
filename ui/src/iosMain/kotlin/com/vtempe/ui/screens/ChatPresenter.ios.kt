package com.vtempe.ui.screens

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import com.vtempe.shared.data.di.KoinProvider
import com.vtempe.shared.data.repo.ChatHistoryStore
import com.vtempe.shared.domain.consent.HealthDataConsentGate
import com.vtempe.shared.domain.repository.AnalyticsRepository
import com.vtempe.shared.domain.repository.LanguagePreferences
import com.vtempe.shared.domain.repository.ProfileRepository
import com.vtempe.shared.domain.usecase.AskAiTrainer
import com.vtempe.ui.presenter.ChatPresenter
import com.vtempe.ui.presenter.ChatPresenterDelegate
import com.vtempe.ui.platform.voice.MicrophonePermissionResult
import com.vtempe.ui.platform.voice.SpeechToText
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

private class IosChatPresenter(
    ask: AskAiTrainer,
    profileRepository: ProfileRepository,
    chatHistoryStore: ChatHistoryStore,
    analytics: AnalyticsRepository,
    languagePrefs: LanguagePreferences,
    speechToText: SpeechToText,
    healthDataConsent: HealthDataConsentGate
) : ChatPresenter {
    private val job = SupervisorJob()
    private val scope = CoroutineScope(Dispatchers.Main + job)
    private val delegate = ChatPresenterDelegate(
        ask = ask,
        profileRepository = profileRepository,
        chatHistoryStore = chatHistoryStore,
        scope = scope,
        // Same language as the app's AI requests, so dictation follows the in-app setting.
        localeProvider = { languagePrefs.getLanguageTag() },
        analytics = analytics,
        speechToText = speechToText,
        healthDataConsent = healthDataConsent
    )
    override val state get() = delegate.state
    override fun updateInput(text: String) = delegate.updateInput(text)
    override fun send() = delegate.send()
    override fun requestVoiceInput() = delegate.requestVoiceInput()
    override fun onMicrophonePermissionResult(result: MicrophonePermissionResult) =
        delegate.onMicrophonePermissionResult(result)
    override fun finishVoiceInput() = delegate.finishVoiceInput()
    override fun cancelVoiceInput() = delegate.cancelVoiceInput()
    override fun interruptVoiceInput() = delegate.interruptVoiceInput()
    override fun dismissVoiceNotice() = delegate.dismissVoiceNotice()
    fun close() = job.cancel()
}

@Composable
actual fun rememberChatPresenter(): ChatPresenter {
    val presenter = remember {
        val koin = requireNotNull(KoinProvider.koin) { "Koin is not started" }
        IosChatPresenter(
            ask = koin.get(),
            profileRepository = koin.get(),
            chatHistoryStore = koin.get(),
            analytics = koin.get(),
            languagePrefs = koin.get(),
            speechToText = koin.get(),
            healthDataConsent = koin.get()
        )
    }
    DisposableEffect(Unit) { onDispose { presenter.close() } }
    return presenter
}
