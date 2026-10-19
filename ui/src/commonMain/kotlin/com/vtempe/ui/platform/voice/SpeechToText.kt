package com.vtempe.ui.platform.voice

import kotlinx.coroutines.flow.Flow

/**
 * Speech-to-text that runs on the device only: the audio never leaves the phone, so a voice
 * message reaches the server as the same reviewed text as a typed one. Platforms that can only
 * recognise speech on their vendor's servers report [isAvailable] = false instead of falling back.
 *
 * Stateful: [stopListening] acts on this instance's running [listen], so each presenter owns its
 * own instance (a Koin `factory`, never a `single`).
 */
interface SpeechToText {
    /** Whether on-device recognition can run here (for [languageTag] where the platform knows). */
    fun isAvailable(languageTag: String?): Boolean

    /**
     * Starts listening when collected. Emits [SpeechEvent.Partial] while the user speaks and ends
     * with one [SpeechEvent.Final] or [SpeechEvent.Failed]. Cancelling the collection discards the
     * recording and releases the microphone.
     */
    fun listen(languageTag: String?): Flow<SpeechEvent>

    /** Stops capturing audio; the running [listen] then delivers its final result. */
    fun stopListening()
}

sealed interface SpeechEvent {
    data class Partial(val text: String) : SpeechEvent
    data class Final(val text: String) : SpeechEvent
    data class Failed(val error: SpeechError) : SpeechEvent
}

enum class SpeechError {
    /** No on-device recognizer on this device. */
    NOT_AVAILABLE,
    /** Microphone or speech-recognition access was revoked. */
    PERMISSION,
    /** The on-device model for the language is not installed. */
    LANGUAGE_UNAVAILABLE,
    /** Nothing that sounded like speech was heard. */
    NO_SPEECH,
    FAILED
}
