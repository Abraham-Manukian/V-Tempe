package com.vtempe.ui.screens

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import com.vtempe.shared.domain.repository.ChatMessage
import com.vtempe.shared.domain.repository.ProfileRepository
import com.vtempe.shared.domain.usecase.AskAiTrainer
import com.vtempe.shared.domain.util.DataResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import com.vtempe.shared.data.di.KoinProvider

private class IosChatPresenter(
    private val ask: AskAiTrainer,
    private val profileRepository: ProfileRepository
) : ChatPresenter {
    private val job = SupervisorJob()
    private val scope = CoroutineScope(Dispatchers.Main + job)

    private val mutableState = MutableStateFlow(ChatState())
    override val state: StateFlow<ChatState> = mutableState

    init {
        scope.launch {
            val coachId = profileRepository.getProfile()?.coachTrainerId
            if (!coachId.isNullOrBlank()) {
                mutableState.update { it.copy(coachTrainerId = coachId) }
            }
        }
    }
    override fun updateInput(text: String) {
        mutableState.update { current ->
            val normalizedState = when (current.sendState) {
                is ChatSendState.Error -> ChatSendState.Idle
                ChatSendState.Success -> ChatSendState.Idle
                else -> current.sendState
            }
            current.copy(input = text, sendState = normalizedState)
        }
    }
    override fun send() {
        val trimmed = mutableState.value.input.trim()
        val currentState = mutableState.value
        if (trimmed.isEmpty() || currentState.sendState is ChatSendState.Loading) return

        val history = currentState.messages
        val newHistory = history + ChatMessage("user", trimmed)
        mutableState.value =
            currentState.copy(messages = newHistory, input = "", sendState = ChatSendState.Loading)

        scope.launch {
            val localeTag: String? = null
            when (val result = ask(history, trimmed, localeTag)) {
                is DataResult.Success -> {
                    mutableState.update { current ->
                        current.copy(
                            messages = current.messages + ChatMessage("assistant", result.data.reply),
                            sendState = ChatSendState.Success
                        )
                    }
                }

                is DataResult.Failure -> {
                    val message = result.message ?: when (result.reason) {
                        DataResult.Reason.Timeout -> "Request timed out. Please try again."
                        DataResult.Reason.Network -> "No network connection."
                        DataResult.Reason.Http -> "Server returned an error (${result.code ?: "unknown"})."
                        DataResult.Reason.InvalidFormat -> "Could not parse the assistant reply."
                        DataResult.Reason.CacheMissing -> "No cached response is available."
                        DataResult.Reason.Unknown -> "Unable to send your message."
                    }
                    mutableState.update { it.copy(sendState = ChatSendState.Error(message)) }
                }
            }
        }
    }

    fun close() {
        job.cancel()
    }
}

@Composable
actual fun rememberChatPresenter(): ChatPresenter {
    val presenter = remember {
        val koin = requireNotNull(KoinProvider.koin) { "Koin is not started" }
        IosChatPresenter(
            ask = koin.get<AskAiTrainer>(),
            profileRepository = koin.get<ProfileRepository>()
        )
    }
    DisposableEffect(Unit) { onDispose { presenter.close() } }
    return presenter
}

