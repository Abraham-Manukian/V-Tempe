package com.vtempe.ui.presenter

import com.vtempe.shared.domain.model.MAX_CHAT_MESSAGE_LENGTH
import com.vtempe.shared.domain.repository.CoachResponse
import com.vtempe.shared.domain.util.DataResult
import com.vtempe.ui.platform.voice.MicrophonePermissionResult
import com.vtempe.ui.platform.voice.SpeechError
import com.vtempe.ui.platform.voice.SpeechEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

/** Dictation in the chat composer: consent first, on-device recognizer, transcript reviewed in the draft. */
class ChatVoiceInputTest {

    private fun TestScope.listening(f: ChatFixture = ChatFixture()): Pair<ChatFixture, ChatPresenterDelegate> {
        val p = f.presenter(this)
        p.requestVoiceInput()
        p.onMicrophonePermissionResult(MicrophonePermissionResult.GRANTED)
        runCurrent()
        assertTrue(f.speech.isListening)
        return f to p
    }

    private fun ChatPresenterDelegate.notice() = (state.value.voice as? VoiceInputState.Notice)?.notice

    @Test
    fun dictatedTextLandsInTheDraftForReviewAndIsNeverSentByItself() = runTest {
        val (f, p) = listening(ChatFixture().apply { locale = "ru" })
        assertEquals("ru", f.speech.lastLanguage, "the chat language goes to the recognizer")

        f.speech.say("how much")
        runCurrent()
        assertEquals(VoiceInputState.Listening(transcript = "how much"), p.state.value.voice)

        p.finishVoiceInput()
        assertEquals(1, f.speech.stops)
        assertEquals(VoiceInputState.Listening(transcript = "how much", finishing = true), p.state.value.voice)

        f.speech.finalResult("how much protein")
        runCurrent()
        assertEquals("how much protein", p.state.value.input)
        assertEquals(VoiceInputState.Idle, p.state.value.voice)
        assertEquals(1, f.speech.released, "the microphone is released")
        assertTrue(f.sent.isEmpty(), "nothing goes to the coach until the user taps Send")
        assertTrue(p.state.value.messages.isEmpty())
    }

    @Test
    fun theReviewedTranscriptIsSentThroughTheRegularChatPath() = runTest {
        val (f, p) = listening()
        f.speech.finalResult("swap squats")
        runCurrent()
        p.updateInput(p.state.value.input + " for lunges") // the user edits the transcript
        val (answer, _) = f.reply("")
        p.send()
        runCurrent()
        assertEquals("swap squats for lunges", f.sent.single().second)
        answer.complete(DataResult.Success(CoachResponse(reply = "done")))
        runCurrent()
        assertIs<ChatSendState.Success>(p.state.value.sendState)
    }

    @Test
    fun theTranscriptIsAppendedToTheTypedDraft() = runTest {
        val f = ChatFixture()
        val p = f.presenter(this)
        p.updateInput("Hi coach,  ")
        p.requestVoiceInput()
        p.onMicrophonePermissionResult(MicrophonePermissionResult.GRANTED)
        runCurrent()
        f.speech.finalResult("  plan my week ")
        runCurrent()
        assertEquals("Hi coach, plan my week", p.state.value.input)
    }

    @Test
    fun withoutHealthDataConsentTheMicrophoneIsNeverOpened() = runTest {
        val f = ChatFixture().apply { consentGranted = false }
        val p = f.presenter(this)
        p.requestVoiceInput()
        assertEquals(VoiceInputNotice.CONSENT_REQUIRED, p.notice())

        p.onMicrophonePermissionResult(MicrophonePermissionResult.GRANTED) // a stray answer
        runCurrent()
        assertEquals(0, f.speech.listens)
    }

    @Test
    fun consentWithdrawnWhileThePermissionDialogIsOpenStopsTheStart() = runTest {
        val f = ChatFixture()
        val p = f.presenter(this)
        p.requestVoiceInput()
        assertEquals(VoiceInputState.AwaitingPermission, p.state.value.voice)
        f.consentGranted = false
        p.onMicrophonePermissionResult(MicrophonePermissionResult.GRANTED)
        runCurrent()
        assertEquals(VoiceInputNotice.CONSENT_REQUIRED, p.notice())
        assertEquals(0, f.speech.listens)
    }

    @Test
    fun aDeviceWithoutOnDeviceRecognitionSaysSoInsteadOfAskingForTheMicrophone() = runTest {
        val f = ChatFixture().apply { speech.available = false }
        val p = f.presenter(this)
        p.requestVoiceInput()
        assertEquals(VoiceInputNotice.UNAVAILABLE, p.notice())
        assertEquals(0, f.speech.listens)
    }

    @Test
    fun aDeniedPermissionExplainsWhyAndRecordsNothing() = runTest {
        val f = ChatFixture()
        val p = f.presenter(this)
        p.requestVoiceInput()
        p.onMicrophonePermissionResult(MicrophonePermissionResult.DENIED)
        assertEquals(VoiceInputNotice.PERMISSION_DENIED, p.notice())

        p.dismissVoiceNotice()
        p.requestVoiceInput()
        p.onMicrophonePermissionResult(MicrophonePermissionResult.BLOCKED)
        assertEquals(VoiceInputNotice.PERMISSION_BLOCKED, p.notice())
        runCurrent()
        assertEquals(0, f.speech.listens)
    }

    @Test
    fun cancelDiscardsTheRecordingAndKeepsTheDraft() = runTest {
        val f = ChatFixture()
        val p = f.presenter(this)
        p.updateInput("typed")
        p.requestVoiceInput()
        p.onMicrophonePermissionResult(MicrophonePermissionResult.GRANTED)
        runCurrent()
        f.speech.say("never mind")
        runCurrent()

        p.cancelVoiceInput()
        runCurrent()
        assertEquals(VoiceInputState.Idle, p.state.value.voice)
        assertEquals("typed", p.state.value.input)
        assertEquals(1, f.speech.released)
        assertFalse(f.speech.isListening)
    }

    @Test
    fun aPermissionAnswerArrivingAfterCancelStartsNothing() = runTest {
        val f = ChatFixture()
        val p = f.presenter(this)
        p.requestVoiceInput()
        p.cancelVoiceInput() // the user left the screen while the dialog was open
        p.onMicrophonePermissionResult(MicrophonePermissionResult.GRANTED)
        runCurrent()
        assertEquals(VoiceInputState.Idle, p.state.value.voice)
        assertEquals(0, f.speech.listens)
    }

    @Test
    fun theTranscriptIsCutAtTheMessageLimitAndTheUserIsTold() = runTest {
        val f = ChatFixture()
        val p = f.presenter(this)
        p.updateInput("a".repeat(MAX_CHAT_MESSAGE_LENGTH - 5))
        p.requestVoiceInput()
        p.onMicrophonePermissionResult(MicrophonePermissionResult.GRANTED)
        runCurrent()
        f.speech.finalResult("one two three")
        runCurrent()
        assertEquals("a".repeat(MAX_CHAT_MESSAGE_LENGTH - 5) + " one", p.state.value.input, "no trailing space is left")
        assertEquals(VoiceInputNotice.TRUNCATED, p.notice())
    }

    @Test
    fun aTranscriptThatExactlyFillsTheLimitIsKeptWhole() = runTest {
        val f = ChatFixture()
        val p = f.presenter(this)
        p.updateInput("a".repeat(MAX_CHAT_MESSAGE_LENGTH - 4))
        p.requestVoiceInput()
        p.onMicrophonePermissionResult(MicrophonePermissionResult.GRANTED)
        runCurrent()
        f.speech.finalResult("abc")
        runCurrent()
        assertEquals(MAX_CHAT_MESSAGE_LENGTH, p.state.value.input.length)
        assertEquals(VoiceInputState.Idle, p.state.value.voice)
    }

    @Test
    fun anEmptyFinalResultKeepsTheLastPartialText() = runTest {
        val (f, p) = listening()
        f.speech.say("more cardio")
        runCurrent()
        f.speech.finalResult("")
        runCurrent()
        assertEquals("more cardio", p.state.value.input)
    }

    @Test
    fun silenceEndsWithANoSpeechNoticeAndAnUnchangedDraft() = runTest {
        val (f, p) = listening()
        f.speech.finalResult("   ")
        runCurrent()
        assertEquals("", p.state.value.input)
        assertEquals(VoiceInputNotice.NO_SPEECH, p.notice())
    }

    @Test
    fun aRecognizerClosingWithoutAVerdictKeepsWhatWasShown() = runTest {
        val (f, p) = listening()
        f.speech.say("easier workout")
        runCurrent()
        f.speech.endSilently()
        runCurrent()
        assertEquals("easier workout", p.state.value.input)
        assertEquals(VoiceInputState.Idle, p.state.value.voice)
    }

    @Test
    fun aRecognizerErrorBeforeAnyTextIsShownAndTheDraftStaysAsItWas() = runTest {
        val (f, p) = listening()
        f.speech.fail(SpeechError.LANGUAGE_UNAVAILABLE)
        runCurrent()
        assertEquals(VoiceInputNotice.LANGUAGE_UNAVAILABLE, p.notice())
        assertEquals("", p.state.value.input)
    }

    @Test
    fun aRecognizerErrorAfterVisibleTextKeepsThatTextInTheDraft() = runTest {
        val (f, p) = listening()
        f.speech.say("make it easier")
        runCurrent()
        f.speech.fail(SpeechError.FAILED)
        runCurrent()
        assertEquals("make it easier", p.state.value.input)
        assertEquals(VoiceInputNotice.FAILED, p.notice())
    }

    @Test
    fun aSpeechTimeoutAfterVisibleTextEndsLikeANormalDictation() = runTest {
        val (f, p) = listening()
        f.speech.say("add cardio")
        runCurrent()
        f.speech.fail(SpeechError.NO_SPEECH)
        runCurrent()
        assertEquals("add cardio", p.state.value.input)
        assertEquals(VoiceInputState.Idle, p.state.value.voice)
    }

    @Test
    fun everySpeechErrorMapsToItsNotice() = runTest {
        val expected = mapOf(
            SpeechError.NOT_AVAILABLE to VoiceInputNotice.UNAVAILABLE,
            SpeechError.PERMISSION to VoiceInputNotice.PERMISSION_BLOCKED,
            SpeechError.LANGUAGE_UNAVAILABLE to VoiceInputNotice.LANGUAGE_UNAVAILABLE,
            SpeechError.NO_SPEECH to VoiceInputNotice.NO_SPEECH,
            SpeechError.FAILED to VoiceInputNotice.FAILED,
        )
        assertEquals(SpeechError.entries.toSet(), expected.keys)
        expected.forEach { (error, notice) ->
            val (f, p) = listening()
            f.speech.fail(error)
            runCurrent()
            assertEquals(notice, p.notice(), "for $error")
        }
    }

    @Test
    fun aRecognizerThatThrowsEndsInAFailureNotice() = runTest {
        val f = ChatFixture()
        f.speech.throwOnListen = IllegalStateException("audio engine")
        val p = f.presenter(this)
        p.requestVoiceInput()
        p.onMicrophonePermissionResult(MicrophonePermissionResult.GRANTED)
        runCurrent()
        assertEquals(VoiceInputNotice.FAILED, p.notice())
    }

    @Test
    fun nothingIsSentWhileStillRecording() = runTest {
        val f = ChatFixture()
        val p = f.presenter(this)
        p.updateInput("typed")
        p.requestVoiceInput()
        p.onMicrophonePermissionResult(MicrophonePermissionResult.GRANTED)
        runCurrent()
        p.send()
        runCurrent()
        assertTrue(f.sent.isEmpty())
        assertEquals("typed", p.state.value.input)
    }

    @Test
    fun dictatingWhileTheCoachAnswersKeepsThatRequestInFlight() = runTest {
        val f = ChatFixture()
        val p = f.presenter(this)
        val (answer, _) = f.reply("")
        p.updateInput("first")
        p.send()
        p.requestVoiceInput()
        p.onMicrophonePermissionResult(MicrophonePermissionResult.GRANTED)
        runCurrent()
        f.speech.finalResult("second")
        runCurrent()
        assertIs<ChatSendState.Loading>(p.state.value.sendState)
        assertEquals("second", p.state.value.input)
        answer.complete(DataResult.Success(CoachResponse(reply = "r")))
        runCurrent()
        assertEquals(listOf("first", "r"), p.state.value.messages.map { it.content })
    }

    @Test
    fun typingDismissesAVoiceNotice() = runTest {
        val f = ChatFixture().apply { speech.available = false }
        val p = f.presenter(this)
        p.requestVoiceInput()
        p.updateInput("x")
        assertEquals(VoiceInputState.Idle, p.state.value.voice)
    }

    @Test
    fun aSecondMicTapWhileRecordingDoesNotOpenAnotherSession() = runTest {
        val (f, p) = listening()
        p.requestVoiceInput()
        p.onMicrophonePermissionResult(MicrophonePermissionResult.GRANTED)
        runCurrent()
        assertEquals(1, f.speech.listens)
    }

    @Test
    fun eventsAfterTheVerdictAreIgnored() = runTest {
        val (f, p) = listening()
        f.speech.emit(SpeechEvent.Final("first"))
        f.speech.emit(SpeechEvent.Final("second"))
        f.speech.emit(SpeechEvent.Partial("third"))
        runCurrent()
        assertEquals("first", p.state.value.input)
        assertEquals(VoiceInputState.Idle, p.state.value.voice)
    }

    @Test
    fun cancelThenANewRecordingKeepsOnlyTheNewSession() = runTest {
        val (f, p) = listening()
        f.speech.say("old words")
        runCurrent()
        p.cancelVoiceInput()
        p.requestVoiceInput()
        p.onMicrophonePermissionResult(MicrophonePermissionResult.GRANTED)
        runCurrent()
        assertEquals(2, f.speech.listens)
        assertEquals(1, f.speech.released)
        assertTrue(f.speech.isListening, "the old session's cleanup does not end the new one")
        assertEquals(VoiceInputState.Listening(), p.state.value.voice)

        f.speech.finalResult("new words")
        runCurrent()
        assertEquals("new words", p.state.value.input)
        assertEquals(2, f.speech.released)
    }

    @Test
    fun dictatedTextIsStillHeldBackWhenConsentIsWithdrawnBeforeSending() = runTest {
        val (f, p) = listening()
        f.speech.finalResult("my knee hurts")
        runCurrent()
        f.consentGranted = false
        f.reply("")
        p.send()
        runCurrent()
        assertEquals(ChatSendState.ConsentRequired, p.state.value.sendState)
        assertTrue(f.sent.isEmpty(), "nothing reached the network")
    }

    @Test
    fun aDraftAlreadyAtTheLimitTakesNoMoreWords() = runTest {
        val f = ChatFixture()
        val p = f.presenter(this)
        val full = "a".repeat(MAX_CHAT_MESSAGE_LENGTH)
        p.updateInput(full)
        p.requestVoiceInput()
        p.onMicrophonePermissionResult(MicrophonePermissionResult.GRANTED)
        runCurrent()
        f.speech.finalResult("more")
        runCurrent()
        assertEquals(full, p.state.value.input)
        assertEquals(VoiceInputNotice.TRUNCATED, p.notice())
    }

    @Test
    fun aTranscriptLongerThanTheLimitIsCutToIt() = runTest {
        val (f, p) = listening()
        f.speech.finalResult("b".repeat(MAX_CHAT_MESSAGE_LENGTH + 1000))
        runCurrent()
        assertEquals(MAX_CHAT_MESSAGE_LENGTH, p.state.value.input.length)
        assertEquals(VoiceInputNotice.TRUNCATED, p.notice())
    }

    @Test
    fun theCutNeverSplitsAnEmoji() = runTest {
        val f = ChatFixture()
        val p = f.presenter(this)
        p.updateInput("a".repeat(MAX_CHAT_MESSAGE_LENGTH - 2))
        p.requestVoiceInput()
        p.onMicrophonePermissionResult(MicrophonePermissionResult.GRANTED)
        runCurrent()
        f.speech.finalResult(EMOJI) // after the joining space only its first UTF-16 unit would fit
        runCurrent()
        assertEquals("a".repeat(MAX_CHAT_MESSAGE_LENGTH - 2), p.state.value.input)
        assertEquals(VoiceInputNotice.TRUNCATED, p.notice())
    }

    @Test
    fun typingNeverLeavesHalfAnEmojiAtTheLimit() = runTest {
        val p = ChatFixture().presenter(this)
        p.updateInput("a".repeat(MAX_CHAT_MESSAGE_LENGTH - 1) + EMOJI)
        assertEquals("a".repeat(MAX_CHAT_MESSAGE_LENGTH - 1), p.state.value.input)
    }

    @Test
    fun dictationClearsAFinishedSendError() = runTest {
        val f = ChatFixture()
        val p = f.presenter(this)
        f.throwOnSend = IllegalStateException("boom")
        p.updateInput("hello")
        p.send()
        runCurrent()
        assertIs<ChatSendState.Error>(p.state.value.sendState)

        p.requestVoiceInput()
        p.onMicrophonePermissionResult(MicrophonePermissionResult.GRANTED)
        runCurrent()
        f.speech.finalResult("again")
        runCurrent()
        assertEquals(ChatSendState.Idle, p.state.value.sendState)
    }

    @Test
    fun doneIsIdempotentAndIgnoredWhenNotRecording() = runTest {
        val f = ChatFixture()
        val p = f.presenter(this)
        p.finishVoiceInput()
        assertEquals(0, f.speech.stops)

        p.requestVoiceInput()
        p.onMicrophonePermissionResult(MicrophonePermissionResult.GRANTED)
        runCurrent()
        p.finishVoiceInput()
        p.finishVoiceInput()
        assertEquals(1, f.speech.stops)
    }

    @Test
    fun aSuggestionPickedWhileRecordingIsKeptAndTheTranscriptFollowsIt() = runTest {
        val (f, p) = listening()
        p.updateInput("Plan my week")
        assertIs<VoiceInputState.Listening>(p.state.value.voice)
        f.speech.finalResult("with two runs")
        runCurrent()
        assertEquals("Plan my week with two runs", p.state.value.input)
    }

    @Test
    fun aBlankPartialDoesNotWipeTheTextOnScreen() = runTest {
        val (f, p) = listening()
        f.speech.say("stretching")
        f.speech.say("")
        runCurrent()
        assertEquals(VoiceInputState.Listening(transcript = "stretching"), p.state.value.voice)
        f.speech.finalResult("")
        runCurrent()
        assertEquals("stretching", p.state.value.input)
    }

    @Test
    fun aRepeatedPermissionAnswerStartsOneRecording() = runTest {
        val f = ChatFixture()
        val p = f.presenter(this)
        p.requestVoiceInput()
        p.onMicrophonePermissionResult(MicrophonePermissionResult.GRANTED)
        p.onMicrophonePermissionResult(MicrophonePermissionResult.GRANTED)
        p.onMicrophonePermissionResult(MicrophonePermissionResult.DENIED)
        runCurrent()
        assertEquals(1, f.speech.listens)
        assertIs<VoiceInputState.Listening>(p.state.value.voice)
    }

    @Test
    fun closingThePresenterReleasesTheMicrophone() = runTest {
        val f = ChatFixture()
        val presenterJob = Job()
        val p = f.presenter(this, CoroutineScope(StandardTestDispatcher(testScheduler) + presenterJob))
        p.requestVoiceInput()
        p.onMicrophonePermissionResult(MicrophonePermissionResult.GRANTED)
        runCurrent()
        assertTrue(f.speech.isListening)

        presenterJob.cancel() // ViewModel cleared or iOS presenter closed while recording
        runCurrent()
        assertEquals(1, f.speech.released)
        assertFalse(f.speech.isListening)
    }

    @Test
    fun cancelClearsAnOldNoticeSoItDoesNotReturnWithTheScreen() = runTest {
        val f = ChatFixture().apply { consentGranted = false }
        val p = f.presenter(this)
        p.requestVoiceInput()
        p.cancelVoiceInput() // the chat screen left composition
        assertEquals(VoiceInputState.Idle, p.state.value.voice)
    }

    @Test
    fun theMicIsOfferedOnlyWhereOnDeviceRecognitionExists() = runTest {
        assertTrue(ChatFixture().presenter(this).state.value.voiceAvailable)
        val f = ChatFixture().apply { speech.available = false }
        assertFalse(f.presenter(this).state.value.voiceAvailable)
    }

    @Test
    fun leavingTheScreenWhileRecordingKeepsTheShownTextAndReleasesTheMicrophone() = runTest {
        val (f, p) = listening()
        f.speech.say("three sets of squats")
        runCurrent()
        p.interruptVoiceInput() // screen left or app backgrounded
        runCurrent()
        assertEquals("three sets of squats", p.state.value.input)
        assertEquals(VoiceInputState.Idle, p.state.value.voice)
        assertEquals(1, f.speech.released)
    }

    @Test
    fun leavingTheScreenBeforeAnyWordsLeavesTheDraftAlone() = runTest {
        val (f, p) = listening()
        p.interruptVoiceInput()
        runCurrent()
        assertEquals("", p.state.value.input)
        assertEquals(VoiceInputState.Idle, p.state.value.voice)
        assertFalse(f.speech.isListening)
    }

    @Test
    fun aRecognizerSilentAfterDoneGivesUpAndKeepsTheShownText() = runTest {
        val (f, p) = listening()
        f.speech.say("swap the deadlift")
        runCurrent()
        p.finishVoiceInput()
        advanceTimeBy(ChatVoiceInput.FINISH_TIMEOUT_MS - 1)
        runCurrent()
        assertIs<VoiceInputState.Listening>(p.state.value.voice, "still waiting for the final result")

        advanceTimeBy(2)
        runCurrent()
        assertEquals("swap the deadlift", p.state.value.input)
        assertEquals(VoiceInputState.Idle, p.state.value.voice)
        assertFalse(f.speech.isListening)
    }

    @Test
    fun theFinishTimeoutDoesNotTouchALaterRecording() = runTest {
        val (f, p) = listening()
        f.speech.say("first")
        runCurrent()
        p.finishVoiceInput()
        f.speech.finalResult("first")
        runCurrent()
        p.requestVoiceInput()
        p.onMicrophonePermissionResult(MicrophonePermissionResult.GRANTED)
        runCurrent()
        advanceTimeBy(ChatVoiceInput.FINISH_TIMEOUT_MS + 1)
        runCurrent()
        assertEquals(VoiceInputState.Listening(), p.state.value.voice)
        assertTrue(f.speech.isListening)
    }

    private companion object {
        /** One emoji: two UTF-16 units. */
        const val EMOJI = "😀"
    }
}
