package com.vtempe.shared.data.consent

import com.vtempe.shared.domain.consent.HealthDataConsentGate
import com.vtempe.shared.domain.model.Advice
import com.vtempe.shared.domain.model.Goal
import com.vtempe.shared.domain.model.NutritionPlan
import com.vtempe.shared.domain.model.Profile
import com.vtempe.shared.domain.model.Sex
import com.vtempe.shared.domain.model.TrainingPlan
import com.vtempe.shared.domain.repository.AiTrainerRepository
import com.vtempe.shared.domain.repository.ChatMessage
import com.vtempe.shared.domain.repository.ChatRepository
import com.vtempe.shared.domain.repository.CoachBundle
import com.vtempe.shared.domain.repository.CoachResponse
import com.vtempe.shared.domain.util.DataResult
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class ConsentGatedAiRepositoriesTest {

    private class Gate(var granted: Boolean) : HealthDataConsentGate {
        override fun isHealthDataConsentGranted() = granted
    }

    private class RecordingAi : AiTrainerRepository {
        val calls = mutableListOf<String>()
        override suspend fun generateTrainingPlan(profile: Profile, weekIndex: Int): DataResult<TrainingPlan> {
            calls += "training"; return DataResult.Success(TrainingPlan(weekIndex, emptyList()))
        }
        override suspend fun generateNutritionPlan(profile: Profile, weekIndex: Int): DataResult<NutritionPlan> {
            calls += "nutrition"; return DataResult.Success(NutritionPlan(weekIndex, emptyMap(), emptyList()))
        }
        override suspend fun getSleepAdvice(profile: Profile): DataResult<Advice> {
            calls += "sleep"; return DataResult.Success(Advice(emptyList()))
        }
        override suspend fun bootstrap(profile: Profile, weekIndex: Int): DataResult<CoachBundle> {
            calls += "bootstrap"; return DataResult.Success(CoachBundle())
        }
    }

    private class RecordingChat : ChatRepository {
        var calls = 0
        override suspend fun send(
            profile: Profile,
            history: List<ChatMessage>,
            userMessage: String,
            locale: String?,
            currentTrainingPlan: TrainingPlan?,
            currentNutritionPlan: NutritionPlan?
        ): DataResult<CoachResponse> {
            calls++
            return DataResult.Success(CoachResponse(reply = "ok"))
        }
    }

    private val profile = Profile(
        id = "p", age = 30, sex = Sex.FEMALE, heightCm = 170, weightKg = 60.0,
        goal = Goal.MAINTAIN, experienceLevel = 2
    )

    @Test
    fun withoutConsentNoAiRequestReachesTheNetwork() = runBlocking {
        val network = RecordingAi()
        val chatNetwork = RecordingChat()
        val gate = Gate(granted = false)
        val ai = ConsentGatedAiTrainerRepository(network, gate)
        val chat = ConsentGatedChatRepository(chatNetwork, gate)

        val results = listOf(
            ai.bootstrap(profile, 0),
            ai.generateTrainingPlan(profile, 0),
            ai.generateNutritionPlan(profile, 0),
            ai.getSleepAdvice(profile),
            chat.send(profile, emptyList(), "my knee hurts", null)
        )

        results.forEach {
            assertEquals(DataResult.Reason.ConsentRequired, assertIs<DataResult.Failure>(it).reason)
        }
        assertTrue(network.calls.isEmpty())
        assertEquals(0, chatNetwork.calls)
    }

    @Test
    fun consentChangesApplyToTheVeryNextRequest() = runBlocking {
        val network = RecordingAi()
        val chatNetwork = RecordingChat()
        val gate = Gate(granted = true)
        val ai = ConsentGatedAiTrainerRepository(network, gate)
        val chat = ConsentGatedChatRepository(chatNetwork, gate)

        assertIs<DataResult.Success<*>>(ai.bootstrap(profile, 0))
        assertIs<DataResult.Success<*>>(chat.send(profile, emptyList(), "hi", null))

        gate.granted = false // withdrawn in Settings
        assertIs<DataResult.Failure>(ai.generateTrainingPlan(profile, 1))
        assertIs<DataResult.Failure>(chat.send(profile, emptyList(), "hi again", null))

        assertEquals(listOf("bootstrap"), network.calls)
        assertEquals(1, chatNetwork.calls)
    }
}
