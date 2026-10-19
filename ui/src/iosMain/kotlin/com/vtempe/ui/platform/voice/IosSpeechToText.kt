package com.vtempe.ui.platform.voice

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.ObjCObjectVar
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.value
import io.github.aakira.napier.Napier
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn
import platform.AVFAudio.AVAudioEngine
import platform.AVFAudio.AVAudioSession
import platform.AVFAudio.AVAudioSessionCategoryOptionDuckOthers
import platform.AVFAudio.AVAudioSessionCategoryRecord
import platform.AVFAudio.AVAudioSessionModeMeasurement
import platform.AVFAudio.AVAudioSessionSetActiveOptionNotifyOthersOnDeactivation
import platform.Foundation.NSError
import platform.Foundation.NSLocale
import platform.Foundation.currentLocale
import platform.Speech.SFSpeechAudioBufferRecognitionRequest
import platform.Speech.SFSpeechRecognizer

/**
 * iOS dictation with `requiresOnDeviceRecognition`: the audio never goes to Apple's servers,
 * which the health-data consent does not cover. Languages without an on-device model report
 * [isAvailable] = false. Not compiled on the Windows dev machine; verify on macOS.
 */
@OptIn(ExperimentalForeignApi::class)
class IosSpeechToText : SpeechToText {

    private var stopAudio: (() -> Unit)? = null

    override fun isAvailable(languageTag: String?): Boolean =
        recognizerFor(languageTag)?.supportsOnDeviceRecognition == true

    override fun listen(languageTag: String?): Flow<SpeechEvent> = callbackFlow {
        val recognizer = recognizerFor(languageTag)
        if (recognizer == null || !recognizer.supportsOnDeviceRecognition) {
            trySend(SpeechEvent.Failed(SpeechError.LANGUAGE_UNAVAILABLE))
            close()
            return@callbackFlow
        }
        val session = AVAudioSession.sharedInstance()
        val previousCategory = session.category
        val request = SFSpeechAudioBufferRecognitionRequest().apply {
            shouldReportPartialResults = true
            requiresOnDeviceRecognition = true
        }
        val engine = AVAudioEngine()
        val input = engine.inputNode
        var tapInstalled = false
        var audioStopped = false
        val stop = {
            if (!audioStopped) {
                audioStopped = true
                engine.stop()
                if (tapInstalled) input.removeTapOnBus(0u)
                request.endAudio()
            }
        }
        val releaseSession = {
            session.setActive(false, AVAudioSessionSetActiveOptionNotifyOthersOnDeactivation, null)
            // Leave the app's audio as it was (playback stays possible after dictation).
            if (previousCategory != null) session.setCategory(previousCategory, null)
        }

        val startError = startAudio(session, engine) {
            input.installTapOnBus(0u, 1024u, input.outputFormatForBus(0u)) { buffer, _ ->
                if (buffer != null) request.appendAudioPCMBuffer(buffer)
            }
            tapInstalled = true
        }
        if (startError != null) {
            // Only the framework's error identity, never anything the user said.
            Napier.w("Voice input could not start: ${startError.domain} ${startError.code}")
            stop()
            releaseSession()
            trySend(SpeechEvent.Failed(SpeechError.FAILED))
            close()
            return@callbackFlow
        }

        // The result handler runs on a Speech framework queue; the channel is thread-safe.
        val task = recognizer.recognitionTaskWithRequest(request) { result, error ->
            when {
                result != null && result.final -> {
                    trySend(SpeechEvent.Final(result.bestTranscription.formattedString))
                    close()
                }
                result != null -> trySend(SpeechEvent.Partial(result.bestTranscription.formattedString))
                error != null -> {
                    trySend(SpeechEvent.Failed(error.toSpeechError()))
                    close()
                }
            }
        }
        stopAudio = stop
        awaitClose {
            // A newer recording may already own the handle (Cancel, then the mic again).
            if (stopAudio === stop) stopAudio = null
            stop()
            task.cancel()
            releaseSession()
        }
    }.flowOn(Dispatchers.Main)

    override fun stopListening() {
        stopAudio?.invoke()
    }
}

/** Prepares the session and starts the engine; returns the failure, or null once audio flows. */
@OptIn(ExperimentalForeignApi::class)
private fun startAudio(session: AVAudioSession, engine: AVAudioEngine, installTap: () -> Unit): NSError? = memScoped {
    val error = alloc<ObjCObjectVar<NSError?>>()
    if (!session.setCategory(AVAudioSessionCategoryRecord, AVAudioSessionModeMeasurement, AVAudioSessionCategoryOptionDuckOthers, error.ptr)) {
        return@memScoped error.value
    }
    if (!session.setActive(true, error.ptr)) return@memScoped error.value
    installTap()
    engine.prepare()
    if (!engine.startAndReturnError(error.ptr)) return@memScoped error.value
    null
}

private fun recognizerFor(languageTag: String?): SFSpeechRecognizer? {
    val locale = recognizerLocale(languageTag)?.let { NSLocale(localeIdentifier = it) } ?: NSLocale.currentLocale
    return SFSpeechRecognizer.alloc()?.initWithLocale(locale)
}

/** The app language as one of the recognizer's own locales ("ru" -> "ru_RU"), the user's region first. */
private fun recognizerLocale(languageTag: String?): String? {
    val region = NSLocale.currentLocale.countryCode
    val supported = SFSpeechRecognizer.supportedLocales()
        .mapNotNull { (it as? NSLocale)?.localeIdentifier }
        .sortedByDescending { region != null && it.endsWith("_$region") }
    return resolveSpeechLocale(languageTag, supported)
}

/** kAFAssistantErrorDomain code for "no speech detected". */
private const val ERROR_NO_SPEECH_DETECTED = 1110L

private fun NSError.toSpeechError(): SpeechError = when (code) {
    ERROR_NO_SPEECH_DETECTED -> SpeechError.NO_SPEECH
    else -> SpeechError.FAILED
}
