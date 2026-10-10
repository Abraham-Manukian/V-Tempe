package com.vtempe.ui.presenter

import com.vtempe.shared.data.repo.ChatHistoryStore
import com.vtempe.shared.data.repo.SettingsAdviceRepository
import com.vtempe.shared.domain.model.Goal
import com.vtempe.shared.domain.model.NutritionPlan
import com.vtempe.shared.domain.model.Profile
import com.vtempe.shared.domain.model.Sex
import com.vtempe.shared.domain.model.TrainingPlan
import com.vtempe.shared.domain.repository.AnalyticsRepository
import com.vtempe.shared.domain.repository.ChatMessage
import com.vtempe.shared.domain.repository.ChatRepository
import com.vtempe.shared.domain.repository.CoachResponse
import com.vtempe.shared.domain.repository.LanguagePreferences
import com.vtempe.shared.domain.repository.ProfileRepository
import com.vtempe.shared.domain.usecase.AskAiTrainer
import com.vtempe.shared.domain.usecase.MaterializeCoachActions
import com.vtempe.shared.domain.util.DataResult
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

/** VT-AUD-066 (one request at a time), 067 (history before the first send), 073 (no endless loading). */
class ChatPresenterStateTest {

    private val profile = Profile("p", 30, Sex.MALE, 180, 80.0, Goal.MAINTAIN, 2)

    private inner class Fixture {
        val settings = mutableMapOf<String, String>()
        val history = ChatHistoryStore(memorySettings(settings))
        var profileGate: CompletableDeferred<Unit>? = null
        val sent = mutableListOf<Pair<List<ChatMessage>, String>>()
        val answers = ArrayDeque<CompletableDeferred<DataResult<CoachResponse>>>()
        var throwOnSend: Exception? = null

        private val profiles = object : ProfileRepository {
            override suspend fun getProfile(): Profile { profileGate?.await(); return profile }
            override suspend fun upsertProfile(profile: Profile) = Unit
            override suspend fun clearAll() = Unit
        }
        private val chat = object : ChatRepository {
            override suspend fun send(
                profile: Profile, history: List<ChatMessage>, userMessage: String, locale: String?,
                currentTrainingPlan: TrainingPlan?, currentNutritionPlan: NutritionPlan?,
            ): DataResult<CoachResponse> {
                throwOnSend?.let { throw it }
                sent += history to userMessage
                return answers.removeFirst().await()
            }
        }

        fun presenter(scope: TestScope): ChatPresenterDelegate {
            val training = FakeTraining()
            val nutrition = FakeNutrition()
            val ask = AskAiTrainer(
                profiles, chat,
                object : LanguagePreferences { override fun getLanguageTag() = "en"; override fun setLanguageTag(tag: String?) = Unit },
                MaterializeCoachActions(profiles, training, nutrition, SettingsAdviceRepository(memorySettings())),
                FakeCoachCache(), training, nutrition,
            )
            return ChatPresenterDelegate(ask, profiles, history, scope.backgroundScope, analytics = stub<AnalyticsRepository> { null })
        }

        fun reply(text: String) = CompletableDeferred<DataResult<CoachResponse>>().also { answers.addLast(it) }.let { it to text }
    }

    private fun ChatPresenterDelegate.type(text: String) { updateInput(text); send() }

    @Test
    fun typingTheNextMessageKeepsTheRequestInFlightAndNothingOverlaps() = runTest {
        val f = Fixture()
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
        val f = Fixture()
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
        val f = Fixture()
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
