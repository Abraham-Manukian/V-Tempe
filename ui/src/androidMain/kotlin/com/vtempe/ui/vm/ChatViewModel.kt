package com.vtempe.ui.vm

import androidx.core.os.LocaleListCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vtempe.shared.domain.repository.ChatMessage
import com.vtempe.shared.domain.repository.PreferencesRepository
import com.vtempe.shared.domain.usecase.AskAiTrainer
import com.vtempe.shared.domain.util.DataResult
import com.vtempe.ui.screens.ChatPresenter
import com.vtempe.ui.screens.ChatSendState
import com.vtempe.ui.screens.ChatState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class ChatViewModel(
    private val ask: AskAiTrainer,
    private val preferencesRepository: PreferencesRepository
) : ViewModel(), ChatPresenter {
    private val _state = MutableStateFlow(ChatState())
    override val state: StateFlow<ChatState> = _state.asStateFlow()

    override fun updateInput(text: String) {
        _state.update { current ->
            val normalizedState = when (current.sendState) {
                is ChatSendState.Error -> ChatSendState.Idle
                ChatSendState.Success -> ChatSendState.Idle
                else -> current.sendState
            }
            current.copy(input = text, sendState = normalizedState)
        }
    }

    override fun send() {
        val trimmed = _state.value.input.trim()
        val currentState = _state.value
        if (trimmed.isEmpty() || currentState.sendState is ChatSendState.Loading) return

        val history = currentState.messages
        val newHistory = history + ChatMessage("user", trimmed)
        _state.value =
            currentState.copy(messages = newHistory, input = "", sendState = ChatSendState.Loading)

        viewModelScope.launch {
            val localeTag = preferencesRepository.getLanguageTag()
                ?: LocaleListCompat.getAdjustedDefault().get(0)?.toLanguageTag()

            val result = runCatching { ask(history, trimmed, localeTag) }
                .getOrElse { throwable ->
                    DataResult.Failure(
                        reason = DataResult.Reason.Unknown,
                        message = throwable.message ?: "Unable to send your message.",
                        throwable = throwable
                    )
                }

            when (result) {
                is DataResult.Success -> {
                    _state.update { current ->
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
                    _state.update { it.copy(sendState = ChatSendState.Error(message)) }
                }
            }
        }

    }
}

