package com.vtempe.ui.platform.voice

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import java.util.Locale
import androidx.annotation.RequiresApi
import androidx.core.os.LocaleListCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.ProducerScope
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn

/**
 * Android dictation through the on-device recognizer only (API 31+). The regular
 * `createSpeechRecognizer` may stream the audio to the recognition vendor's servers, which the
 * health-data consent does not cover, so devices without an on-device recognizer get no voice input.
 * [SpeechRecognizer] must be used on the main thread; the flow is pinned to it.
 */
class AndroidSpeechToText(private val context: Context) : SpeechToText {

    private var active: SpeechRecognizer? = null

    /** Android cannot tell before listening whether the language pack is installed; [listen] reports it. */
    override fun isAvailable(languageTag: String?): Boolean = onDeviceRecognitionAvailable()

    override fun listen(languageTag: String?): Flow<SpeechEvent> = callbackFlow {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S || !onDeviceRecognitionAvailable()) {
            trySend(SpeechEvent.Failed(SpeechError.NOT_AVAILABLE))
            close()
            return@callbackFlow
        }
        val recognizer = startOnDevice(languageTag)
        awaitClose {
            if (active === recognizer) active = null
            recognizer.cancel()
            recognizer.destroy()
        }
    }.flowOn(Dispatchers.Main.immediate)

    private fun onDeviceRecognitionAvailable(): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && SpeechRecognizer.isOnDeviceRecognitionAvailable(context)

    @RequiresApi(Build.VERSION_CODES.S)
    private fun ProducerScope<SpeechEvent>.startOnDevice(languageTag: String?): SpeechRecognizer {
        val recognizer = SpeechRecognizer.createOnDeviceSpeechRecognizer(context)
        try {
            listenWith(recognizer, languageTag)
        } catch (error: RuntimeException) {
            // awaitClose is not registered yet: release the service binding here, then fail the flow.
            recognizer.destroy()
            throw error
        }
        active = recognizer
        return recognizer
    }

    @RequiresApi(Build.VERSION_CODES.S)
    private fun ProducerScope<SpeechEvent>.listenWith(recognizer: SpeechRecognizer, languageTag: String?) {
        recognizer.setRecognitionListener(object : RecognitionListener {
            override fun onPartialResults(partialResults: Bundle?) {
                trySend(SpeechEvent.Partial(partialResults.bestText()))
            }

            override fun onResults(results: Bundle?) {
                trySend(SpeechEvent.Final(results.bestText()))
                close()
            }

            override fun onError(error: Int) {
                trySend(SpeechEvent.Failed(error.toSpeechError()))
                close()
            }

            override fun onReadyForSpeech(params: Bundle?) = Unit
            override fun onBeginningOfSpeech() = Unit
            override fun onRmsChanged(rmsdB: Float) = Unit
            override fun onBufferReceived(buffer: ByteArray?) = Unit
            override fun onEndOfSpeech() = Unit
            override fun onEvent(eventType: Int, params: Bundle?) = Unit
        })
        recognizer.startListening(recognizerIntent(context.packageName, languageTag))
    }

    override fun stopListening() {
        active?.stopListening()
    }
}

private fun recognizerIntent(packageName: String, languageTag: String?) =
    Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, packageName)
        speechLanguage(languageTag)?.let { putExtra(RecognizerIntent.EXTRA_LANGUAGE, it) }
    }

/** The app language as a regional tag where the system locales know one ("ru" -> "ru-RU"). */
private fun speechLanguage(languageTag: String?): String? {
    val systemLocales = LocaleListCompat.getAdjustedDefault()
    val candidates = (0 until systemLocales.size()).mapNotNull { systemLocales.get(it)?.toLanguageTag() }
    return resolveSpeechLocale(languageTag, candidates)?.let { Locale.forLanguageTag(it.replace('_', '-')).toLanguageTag() }
}

private fun Bundle?.bestText(): String =
    this?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty()

// ERROR_LANGUAGE_NOT_SUPPORTED and ERROR_LANGUAGE_UNAVAILABLE exist since API 31, the only level
// the on-device recognizer runs on.
@RequiresApi(Build.VERSION_CODES.S)
internal fun Int.toSpeechError(): SpeechError = when (this) {
    SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> SpeechError.NO_SPEECH
    SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> SpeechError.PERMISSION
    SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED, SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE -> SpeechError.LANGUAGE_UNAVAILABLE
    else -> SpeechError.FAILED
}
