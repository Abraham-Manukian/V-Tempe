package com.vtempe.ui.presenter

import com.vtempe.shared.domain.model.MAX_CHAT_MESSAGE_LENGTH
import androidx.compose.runtime.Immutable
import com.vtempe.shared.data.repo.ChatHistoryStore
import com.vtempe.shared.domain.consent.HealthDataConsentGate
import com.vtempe.shared.domain.model.CoachTrainerIds
import com.vtempe.shared.domain.repository.AnalyticsEvents
import com.vtempe.shared.domain.repository.AnalyticsRepository
import com.vtempe.shared.domain.repository.ChatMessage
import com.vtempe.shared.domain.repository.ProfileRepository
import com.vtempe.shared.domain.usecase.AskAiTrainer
import com.vtempe.shared.domain.util.DataResult
import com.vtempe.ui.platform.voice.MicrophonePermissionResult
import com.vtempe.ui.platform.voice.SpeechToText
import io.github.aakira.napier.Napier
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
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
    /** Blocked on the device: no consent to send health data to the AI coach. */
    data object ConsentRequired : ChatSendState
}

@Immutable
data class ChatState(
    val messages: List<ChatMessage> = emptyList(),
    val input: String = "",
    val sendState: ChatSendState = ChatSendState.Idle,
    val coachTrainerId: String = CoachTrainerIds.DEFAULT,
    val voice: VoiceInputState = VoiceInputState.Idle,
    /** On-device speech recognition exists here; the mic button is hidden otherwise. */
    val voiceAvailable: Boolean = false
)

/** Editing the draft clears a finished send's result but keeps a request in flight (VT-AUD-066). */
internal fun ChatSendState.afterDraftEdit(): ChatSendState =
    if (this == ChatSendState.Loading) ChatSendState.Loading else ChatSendState.Idle

/** Cuts [text] to the chat message limit without splitting a surrogate pair (emoji). */
internal fun clipToMessageLimit(text: String): String {
    if (text.length <= MAX_CHAT_MESSAGE_LENGTH) return text
    val end = if (text[MAX_CHAT_MESSAGE_LENGTH - 1].isHighSurrogate()) MAX_CHAT_MESSAGE_LENGTH - 1 else MAX_CHAT_MESSAGE_LENGTH
    return text.substring(0, end)
}

interface ChatPresenter {
    val state: StateFlow<ChatState>
    fun updateInput(text: String)
    fun send()

    /** Mic tap: checks consent and availability, then asks the screen for the permission. */
    fun requestVoiceInput()
    fun onMicrophonePermissionResult(result: MicrophonePermissionResult)
    /** Done: stop recording and put the transcript into the draft for review. */
    fun finishVoiceInput()
    /** Cancel button: discards the recording. */
    fun cancelVoiceInput()
    /** Screen left or app backgrounded: releases the microphone, keeps the text already shown. */
    fun interruptVoiceInput()
    fun dismissVoiceNotice()
}

class ChatPresenterDelegate(
    private val ask: AskAiTrainer,
    private val profileRepository: ProfileRepository,
    private val chatHistoryStore: ChatHistoryStore,
    private val scope: CoroutineScope,
    /** Platform hook: returns the locale string to pass to the AI. */
    private val localeProvider: () -> String? = { null },
    private val analytics: AnalyticsRepository,
    speechToText: SpeechToText,
    healthDataConsent: HealthDataConsentGate
) : ChatPresenter {

    // The stored history is in place before anything can be sent (VT-AUD-067): a send that raced
    // a late-loading history used to save a one-message dialogue over it, then the late load
    // brought the old one back on screen.
    private val _state = MutableStateFlow(
        ChatState(messages = runCatching { chatHistoryStore.load() }.getOrDefault(emptyList()))
    )
    override val state: StateFlow<ChatState> = _state.asStateFlow()
    private var sendJob: Job? = null
    private val voiceInput = ChatVoiceInput(_state, speechToText, healthDataConsent, scope, localeProvider)

    init {
        scope.launch {
            // Only the coach avatar comes from the profile; it never touches the messages.
            val profile = runCatching { profileRepository.getProfile() }
                .onFailure { if (it is CancellationException) throw it }
                .getOrNull()
            _state.update { it.copy(coachTrainerId = profile?.coachTrainerId ?: CoachTrainerIds.DEFAULT) }
        }
    }

    override fun updateInput(text: String) {
        // Typing the next message while the coach answers keeps the request in flight (VT-AUD-066).
        _state.update {
            it.copy(
                input = clipToMessageLimit(text),
                sendState = it.sendState.afterDraftEdit(),
                voice = if (it.voice is VoiceInputState.Notice) VoiceInputState.Idle else it.voice
            )
        }
    }

    override fun requestVoiceInput() = voiceInput.request()
    override fun onMicrophonePermissionResult(result: MicrophonePermissionResult) = voiceInput.onPermissionResult(result)
    override fun finishVoiceInput() = voiceInput.finish()
    override fun cancelVoiceInput() = voiceInput.cancel()
    override fun interruptVoiceInput() = voiceInput.interrupt()
    override fun dismissVoiceNotice() = voiceInput.dismissNotice()

    override fun send() {
        val current = _state.value
        val text = current.input.trim()
        // One question at a time: the next one is sent with the previous answer in its history.
        if (text.isBlank() || sendJob?.isActive == true) return
        // The transcript is reviewed in the draft first; nothing is sent while still recording.
        if (current.voice is VoiceInputState.Listening) return

        val userMsg = ChatMessage(role = "user", content = text)
        // Include all messages (including plan_change cards) in the displayed list
        val displayMessages = current.messages + userMsg
        // Only real conversation turns go to the AI (plan_change cards are UI-only)
        val aiHistory = current.messages.filter { it.role == "user" || it.role == "assistant" }
        _state.update { it.copy(messages = displayMessages, input = "", sendState = ChatSendState.Loading) }
        // Persist immediately so the user message survives if the app is killed mid-request
        chatHistoryStore.save(aiHistory + userMsg)
        analytics.logEvent(AnalyticsEvents.CHAT_MESSAGE_SENT)

        sendJob = scope.launch {
            val result = try {
                ask(
                    history = aiHistory,
                    userMessage = text,
                    localeOverride = localeProvider()
                )
            } catch (cancelled: CancellationException) {
                _state.update { it.copy(sendState = ChatSendState.Idle) }
                throw cancelled
            } catch (error: Exception) {
                // Storage or plan-materialization failures used to escape the scope and leave the
                // screen loading forever (VT-AUD-073).
                // Reported once below, like any other failure.
                DataResult.Failure(DataResult.Reason.Unknown, throwable = error)
            }
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
                    if (result.reason == DataResult.Reason.ConsentRequired) {
                        // Nothing left the device — not an error worth reporting.
                        _state.update { it.copy(sendState = ChatSendState.ConsentRequired) }
                        return@launch
                    }
                    Napier.w("Chat error: ${result.message}", result.throwable)
                    // Only the machine-readable reason/status go to crash reporting — result.message
                    // can carry the server's response body (and with it the coach's reply).
                    result.throwable?.let {
                        analytics.recordNonFatal(it, "Chat send failed: reason=${result.reason} code=${result.code}")
                    }
                    _state.update { it.copy(sendState = ChatSendState.Error(result.message ?: "Unknown error")) }
                }
            }
        }
    }
}
