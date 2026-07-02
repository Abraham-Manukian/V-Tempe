package com.vtempe.ui.screens

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import com.vtempe.shared.data.di.KoinProvider
import com.vtempe.shared.data.repo.ChatHistoryStore
import com.vtempe.shared.domain.repository.AnalyticsRepository
import com.vtempe.shared.domain.repository.ProfileRepository
import com.vtempe.shared.domain.usecase.AskAiTrainer
import com.vtempe.ui.presenter.ChatPresenter
import com.vtempe.ui.presenter.ChatPresenterDelegate
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

private class IosChatPresenter(
    ask: AskAiTrainer,
    profileRepository: ProfileRepository,
    chatHistoryStore: ChatHistoryStore,
    analytics: AnalyticsRepository
) : ChatPresenter {
    private val job = SupervisorJob()
    private val scope = CoroutineScope(Dispatchers.Main + job)
    private val delegate = ChatPresenterDelegate(
        ask = ask,
        profileRepository = profileRepository,
        chatHistoryStore = chatHistoryStore,
        scope = scope,
        analytics = analytics
    )
    override val state get() = delegate.state
    override fun updateInput(text: String) = delegate.updateInput(text)
    override fun send() = delegate.send()
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
            analytics = koin.get()
        )
    }
    DisposableEffect(Unit) { onDispose { presenter.close() } }
    return presenter
}
