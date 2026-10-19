package com.vtempe.ui.presenter

import com.vtempe.shared.domain.consent.HealthDataConsentGate
import com.vtempe.ui.platform.voice.MicrophonePermissionResult
import com.vtempe.ui.platform.voice.SpeechError
import com.vtempe.ui.platform.voice.SpeechEvent
import com.vtempe.ui.platform.voice.SpeechToText
import io.github.aakira.napier.Napier
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface VoiceInputState {
    data object Idle : VoiceInputState
    /** The screen must ask the platform for the microphone permission. */
    data object AwaitingPermission : VoiceInputState
    /** Recording: [transcript] is the live partial text; [finishing] after the user tapped Done. */
    data class Listening(val transcript: String = "", val finishing: Boolean = false) : VoiceInputState
    data class Notice(val notice: VoiceInputNotice) : VoiceInputState
}

enum class VoiceInputNotice {
    CONSENT_REQUIRED,
    UNAVAILABLE,
    PERMISSION_DENIED,
    PERMISSION_BLOCKED,
    LANGUAGE_UNAVAILABLE,
    NO_SPEECH,
    FAILED,
    /** The transcript did not fit into the chat message limit and was cut. */
    TRUNCATED
}

/**
 * Dictation for the chat composer. A voice message is health-related content, so it starts only
 * with the health-data consent; the transcript lands in the draft for the user to check and send
 * like typed text (never sent automatically), so the regular chat path applies its consent gate,
 * length cap and error handling. The transcript is never logged.
 */
internal class ChatVoiceInput(
    private val state: MutableStateFlow<ChatState>,
    private val speech: SpeechToText,
    private val consent: HealthDataConsentGate,
    private val scope: CoroutineScope,
    private val localeProvider: () -> String?
) {
    private var job: Job? = null
    private var finishTimeout: Job? = null

    init {
        // Devices without on-device recognition get no mic button at all.
        state.update { it.copy(voiceAvailable = speech.isAvailable(localeProvider())) }
    }

    fun request() {
        val voice = state.value.voice
        if (voice is VoiceInputState.Listening || voice == VoiceInputState.AwaitingPermission) return
        setVoice(
            when {
                !consent.isHealthDataConsentGranted() -> VoiceInputState.Notice(VoiceInputNotice.CONSENT_REQUIRED)
                !speech.isAvailable(localeProvider()) -> VoiceInputState.Notice(VoiceInputNotice.UNAVAILABLE)
                else -> VoiceInputState.AwaitingPermission
            }
        )
    }

    fun onPermissionResult(result: MicrophonePermissionResult) {
        // A late answer after Cancel or after leaving the screen starts nothing.
        if (state.value.voice != VoiceInputState.AwaitingPermission) return
        when (result) {
            MicrophonePermissionResult.GRANTED -> start()
            MicrophonePermissionResult.DENIED -> setVoice(VoiceInputState.Notice(VoiceInputNotice.PERMISSION_DENIED))
            MicrophonePermissionResult.BLOCKED -> setVoice(VoiceInputState.Notice(VoiceInputNotice.PERMISSION_BLOCKED))
        }
    }

    fun finish() {
        val voice = state.value.voice
        if (voice !is VoiceInputState.Listening || voice.finishing) return
        setVoice(voice.copy(finishing = true))
        speech.stopListening()
        // A recognizer that never answers after the stop must not leave "Recognizing…" spinning.
        finishTimeout?.cancel()
        finishTimeout = scope.launch {
            delay(FINISH_TIMEOUT_MS)
            if ((state.value.voice as? VoiceInputState.Listening)?.finishing == true) interrupt()
        }
    }

    /** The user's Cancel: the recording is discarded. Also clears a notice. */
    fun cancel() {
        stopSession()
        setVoice(VoiceInputState.Idle)
    }

    /**
     * The screen left, the app went to the background or the recognizer stalled: the microphone
     * is released and the text the user already saw is kept in the draft. Also clears a notice,
     * so an old banner does not come back when the chat is reopened.
     */
    fun interrupt() {
        val shown = (state.value.voice as? VoiceInputState.Listening)?.transcript.orEmpty()
        stopSession()
        if (shown.isNotBlank()) applyTranscript(shown) else setVoice(VoiceInputState.Idle)
    }

    private fun stopSession() {
        finishTimeout?.cancel()
        finishTimeout = null
        job?.cancel()
        job = null
    }

    fun dismissNotice() {
        if (state.value.voice is VoiceInputState.Notice) setVoice(VoiceInputState.Idle)
    }

    private fun start() {
        // Consent can be withdrawn while the permission dialog is open.
        if (!consent.isHealthDataConsentGranted()) {
            setVoice(VoiceInputState.Notice(VoiceInputNotice.CONSENT_REQUIRED))
            return
        }
        stopSession()
        setVoice(VoiceInputState.Listening())
        job = scope.launch {
            var lastPartial = ""
            try {
                speech.listen(localeProvider()).collect { event ->
                    if (state.value.voice !is VoiceInputState.Listening) return@collect
                    when (event) {
                        // A blank partial (a recognizer flicker) must not wipe the text on screen.
                        is SpeechEvent.Partial -> if (event.text.isNotBlank()) {
                            lastPartial = event.text
                            state.update { s ->
                                val listening = s.voice as? VoiceInputState.Listening ?: return@update s
                                s.copy(voice = listening.copy(transcript = event.text))
                            }
                        }
                        // Some recognizers return an empty final after the user stopped mid-phrase.
                        is SpeechEvent.Final -> applyTranscript(event.text.ifBlank { lastPartial })
                        // What the user already saw is kept in the draft; only the notice is added.
                        is SpeechEvent.Failed -> if (lastPartial.isBlank()) {
                            setVoice(VoiceInputState.Notice(event.error.toNotice()))
                        } else {
                            applyTranscript(lastPartial, event.error.takeUnless { it == SpeechError.NO_SPEECH }?.toNotice())
                        }
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                // The recognizer's failure only, never the words.
                Napier.w("Voice input failed: ${error::class.simpleName}")
                setVoice(VoiceInputState.Notice(VoiceInputNotice.FAILED))
            }
            // The recognizer closed without a verdict: keep what the user saw on screen.
            if (state.value.voice is VoiceInputState.Listening) applyTranscript(lastPartial)
        }
    }

    private fun applyTranscript(text: String, notice: VoiceInputNotice? = null) {
        val spoken = text.trim()
        if (spoken.isEmpty()) {
            setVoice(VoiceInputState.Notice(VoiceInputNotice.NO_SPEECH))
            return
        }
        state.update { s ->
            val draft = s.input.trimEnd()
            val combined = if (draft.isEmpty()) spoken else "$draft $spoken"
            val input = clipToMessageLimit(combined).trimEnd()
            val truncated = input.length < combined.length
            s.copy(
                input = input,
                voice = when {
                    truncated -> VoiceInputState.Notice(VoiceInputNotice.TRUNCATED)
                    notice != null -> VoiceInputState.Notice(notice)
                    else -> VoiceInputState.Idle
                },
                sendState = s.sendState.afterDraftEdit()
            )
        }
    }

    private fun setVoice(voice: VoiceInputState) = state.update { it.copy(voice = voice) }

    private fun SpeechError.toNotice(): VoiceInputNotice = when (this) {
        SpeechError.NOT_AVAILABLE -> VoiceInputNotice.UNAVAILABLE
        SpeechError.PERMISSION -> VoiceInputNotice.PERMISSION_BLOCKED
        SpeechError.LANGUAGE_UNAVAILABLE -> VoiceInputNotice.LANGUAGE_UNAVAILABLE
        SpeechError.NO_SPEECH -> VoiceInputNotice.NO_SPEECH
        SpeechError.FAILED -> VoiceInputNotice.FAILED
    }

    internal companion object {
        /** How long the recognizer may take for its final result after Done. */
        const val FINISH_TIMEOUT_MS = 5_000L
    }
}
