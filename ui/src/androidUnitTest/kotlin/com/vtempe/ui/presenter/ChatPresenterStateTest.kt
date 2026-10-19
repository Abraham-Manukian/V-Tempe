package com.vtempe.ui.presenter

import com.vtempe.shared.domain.repository.ChatMessage
import com.vtempe.shared.domain.repository.CoachResponse
import com.vtempe.shared.domain.util.DataResult
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

/** VT-AUD-066 (one request at a time), 067 (history before the first send), 073 (no endless loading). */
class ChatPresenterStateTest {

    private fun ChatPresenterDelegate.type(text: String) { updateInput(text); send() }

    @Test
    fun typingTheNextMessageKeepsTheRequestInFlightAndNothingOverlaps() = runTest {
        val f = ChatFixture()
        val p = f.presenter(this)
        val (first, _) = f.reply("")
        p.type("first")
        runCurrent()

        p.updateInput("second") // drafting while the coach answers
        assertIs<ChatSendState.Loading>(p.state.value.sendState)
        p.send()
        runCurrent()
        assertEquals(1, f.sent.size, "no second request while the first is open")

        first.complete(DataResult.Success(CoachResponse(reply = "reply-first")))
        runCurrent()
        assertIs<ChatSendState.Success>(p.state.value.sendState)

        val (second, _) = f.reply("")
        p.send()
        runCurrent()
        assertEquals(listOf("first", "reply-first"), f.sent.last().first.map { it.content }, "the answer is in the next request's history")
        second.complete(DataResult.Success(CoachResponse(reply = "reply-second")))
        runCurrent()
        assertEquals(listOf("first", "reply-first", "second", "reply-second"), p.state.value.messages.map { it.content })
    }

    @Test
    fun theStoredDialogueIsThereBeforeTheFirstSendAndIsNeverRolledBack() = runTest {
        val f = ChatFixture()
        f.history.save(listOf(ChatMessage("user", "persisted-before-open"), ChatMessage("assistant", "old answer")))
        f.profileGate = CompletableDeferred() // the profile (coach avatar) is slow to load
        val p = f.presenter(this)
        assertEquals(listOf("persisted-before-open", "old answer"), p.state.value.messages.map { it.content })

        val (answer, _) = f.reply("")
        p.type("new message")
        runCurrent()
        assertEquals(listOf("persisted-before-open", "old answer", "new message"), f.history.load().map { it.content })

        f.profileGate!!.complete(Unit)
        runCurrent()
        answer.complete(DataResult.Success(CoachResponse(reply = "new answer")))
        runCurrent()
        val expected = listOf("persisted-before-open", "old answer", "new message", "new answer")
        assertEquals(expected, p.state.value.messages.map { it.content })
        assertEquals(expected, f.history.load().map { it.content })
    }

    @Test
    fun anUnexpectedFailureEndsLoadingAndCanBeRetried() = runTest {
        val f = ChatFixture()
        val p = f.presenter(this)
        f.throwOnSend = IllegalStateException("plan storage failed")
        p.type("hello")
        runCurrent()
        assertIs<ChatSendState.Error>(p.state.value.sendState)

        f.throwOnSend = null
        val (answer, _) = f.reply("")
        p.type("hello again")
        runCurrent()
        answer.complete(DataResult.Success(CoachResponse(reply = "hi")))
        runCurrent()
        assertIs<ChatSendState.Success>(p.state.value.sendState)
    }
}
