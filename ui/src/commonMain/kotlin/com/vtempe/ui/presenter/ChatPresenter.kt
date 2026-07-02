package com.vtempe.ui.presenter

import androidx.compose.runtime.Immutable
import com.vtempe.shared.data.repo.ChatHistoryStore
import com.vtempe.shared.domain.model.CoachTrainerIds
import com.vtempe.shared.domain.repository.AnalyticsEvents
import com.vtempe.shared.domain.repository.AnalyticsRepository
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
    private val chatHistoryStore: ChatHistoryStore,
    private val scope: CoroutineScope,
    /** Platform hook: returns the locale string to pass to the AI. */
    private val localeProvider: () -> String? = { null },
    private val analytics: AnalyticsRepository
) : ChatPresenter {

    private val _state = MutableStateFlow(ChatState())
    override val state: StateFlow<ChatState> = _state.asStateFlow()

    init {
        scope.launch {
            // Load persisted history + coach avatar in parallel
            val history = chatHistoryStore.load()
            val profile = runCatching { profileRepository.getProfile() }.getOrNull()
            _state.update {
                it.copy(
                    messages = history,
                    coachTrainerId = profile?.coachTrainerId ?: CoachTrainerIds.DEFAULT
                )
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
        // Include all messages (including plan_change cards) in the displayed list
        val displayMessages = current.messages + userMsg
        // Only real conversation turns go to the AI (plan_change cards are UI-only)
        val aiHistory = current.messages.filter { it.role == "user" || it.role == "assistant" }
        _state.update { it.copy(messages = displayMessages, input = "", sendState = ChatSendState.Loading) }
        // Persist immediately so the user message survives if the app is killed mid-request
        chatHistoryStore.save(aiHistory + userMsg)
        analytics.logEvent(AnalyticsEvents.CHAT_MESSAGE_SENT)

        scope.launch {
            val result = ask(
                history = aiHistory,
                userMessage = text,
                localeOverride = localeProvider()
            )
            when (result) {
                is DataResult.Success -> {
                    val assistantMsg = ChatMessage(role = "assistant", content = result.data.reply)
                    // Add visual change cards for any plan sections the AI updated
                    val changeCards = buildList {
                        if (result.data.trainingPlan != null)  add(ChatMessage("plan_change", "training"))
                        if (result.data.nutritionPlan != null) add(ChatMessage("plan_change", "nutrition"))
                        if (result.data.sleepAdvice != null)   add(ChatMessage("plan_change", "sleep"))
                    }
                    val updatedMessages = _state.value.messages + assistantMsg + changeCards
                    _state.update { it.copy(messages = updatedMessages, sendState = ChatSendState.Success) }
                    // Persist only real conversation messages (plan_change cards are UI-only)
                    chatHistoryStore.save(updatedMessages.filter { it.role == "user" || it.role == "assistant" })
                }
                is DataResult.Failure -> {
                    Napier.w("Chat error: ${result.message}", result.throwable)
                    result.throwable?.let { analytics.recordNonFatal(it, "Chat send failed: ${result.message}") }
                    _state.update { it.copy(sendState = ChatSendState.Error(result.message ?: "Unknown error")) }
                }
            }
        }
    }
}
