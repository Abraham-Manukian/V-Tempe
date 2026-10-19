package com.vtempe.ui.platform.voice

import android.speech.SpeechRecognizer
import kotlin.test.Test
import kotlin.test.assertEquals

class AndroidSpeechErrorMappingTest {

    @Test
    fun recognizerErrorCodesMapToWhatTheUserIsTold() {
        val expected = mapOf(
            SpeechRecognizer.ERROR_NO_MATCH to SpeechError.NO_SPEECH,
            SpeechRecognizer.ERROR_SPEECH_TIMEOUT to SpeechError.NO_SPEECH,
            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS to SpeechError.PERMISSION,
            SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED to SpeechError.LANGUAGE_UNAVAILABLE,
            SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE to SpeechError.LANGUAGE_UNAVAILABLE,
            SpeechRecognizer.ERROR_NETWORK to SpeechError.FAILED,
            SpeechRecognizer.ERROR_CLIENT to SpeechError.FAILED,
            SpeechRecognizer.ERROR_RECOGNIZER_BUSY to SpeechError.FAILED,
        )
        expected.forEach { (code, error) -> assertEquals(error, code.toSpeechError(), "code $code") }
    }
}
