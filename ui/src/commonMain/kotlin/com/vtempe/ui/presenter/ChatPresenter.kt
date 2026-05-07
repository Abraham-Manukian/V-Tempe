package com.vtempe.ui.presenter

import androidx.compose.runtime.Immutable
import com.vtempe.shared.domain.model.CoachTrainerIds
import com.vtempe.shared.domain.repository.ChatMessage
import com.vtempe.shared.domain.repository.ProfileRepository
import com.vtempe.shared.domain.usecase.AskAiTrainer
import com.vtempe.shared.domain.util.DataResult
import io.github.aakira.napier.Napier
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface ChatSendState {
    data object Idle : ChatSendState
    data object Loading : ChatSendState
    data object Success : ChatSendState
    data class Error(val message: String) : ChatSendState
}

@Immutable
data class ChatState(
    val messages: List<ChatMessage> = emptyList(),
    val input: String = "",
    val sendState: ChatSendState = ChatSendState.Idle,
    val coachTrainerId: String = CoachTrainerIds.DEFAULT
)

interface ChatPresenter {
    val state: StateFlow<ChatState>
    fun updateInput(text: String)
    fun send()
}

class ChatPresenterDelegate(
    private val ask: AskAiTrainer,
    private val profileRepository: ProfileRepository,
    private val scope: CoroutineScope,
    /** Platform hook: returns the locale string to pass to the AI.
     *  Android: stored pref with device-locale fallback. iOS: null (AskAiTrainer reads languagePrefs). */
    private val localeProvider: () -> String? = { null }
) : ChatPresenter {

    private val _state = MutableStateFlow(ChatState())
    override val state: StateFlow<ChatState> = _state.asStateFlow()

    init {
        scope.launch {
            val profile = runCatching { profileRepository.getProfile() }.getOrNull()
            if (profile != null) {
                _state.update { it.copy(coachTrainerId = profile.coachTrainerId) }
            }
        }
    }

    override fun updateInput(text: String) {
        _state.update { it.copy(input = text, sendState = ChatSendState.Idle) }
    }

    override fun send() {
        val current = _state.value
        val text = current.input.trim()
        if (text.isBlank() || current.sendState == ChatSendState.Loading) return

        val userMsg = ChatMessage(role = "user", content = text)
        val history = current.messages + userMsg
        _state.update { it.copy(messages = history, input = "", sendState = ChatSendState.Loading) }

        scope.launch {
            val result = ask(
                history = current.messages,
                userMessage = text,
                localeOverride = localeProvider()
            )
            when (result) {
                is DataResult.Success -> {
                    val assistantMsg = ChatMessage(role = "assistant", content = result.data.reply)
                    _state.update { it.copy(messages = it.messages + assistantMsg, sendState = ChatSendState.Success) }
                }
                is DataResult.Failure -> {
                    Napier.w("Chat error: ${result.message}", result.throwable)
                    _state.update { it.copy(sendState = ChatSendState.Error(result.message ?: "Unknown error")) }
                }
            }
        }
    }
}
