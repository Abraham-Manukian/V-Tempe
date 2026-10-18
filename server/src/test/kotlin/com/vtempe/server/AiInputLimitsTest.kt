package com.vtempe.server

import com.vtempe.server.features.ai.access.AiAccessConfig
import com.vtempe.server.features.ai.access.AiAccessResolver
import com.vtempe.server.features.ai.access.PaidAccessPolicy
import com.vtempe.server.features.ai.domain.AiInputLimits
import com.vtempe.server.features.ai.domain.PromptTooLargeException
import com.vtempe.server.features.ai.api.registerAiRoutes
import com.vtempe.server.features.ai.data.catalog.BuiltInExerciseCatalog
import com.vtempe.server.features.ai.data.llm.LLMClient
import com.vtempe.server.features.ai.data.llm.LlmRepairer
import com.vtempe.server.features.ai.data.llm.decode.Decoder
import com.vtempe.server.features.ai.data.llm.extract.ResponseExtractor
import com.vtempe.server.features.ai.data.llm.feedback.FeedbackComposer
import com.vtempe.server.features.ai.data.llm.pipeline.LlmPipeline
import com.vtempe.server.features.ai.data.llm.pipeline.PipelineConfig
import com.vtempe.server.features.ai.data.llm.repair.JsonSanitizer
import com.vtempe.server.features.ai.data.llm.schema.ResponseSchema
import com.vtempe.server.features.ai.data.llm.telemetry.LlmErrorTracker
import com.vtempe.server.features.ai.data.llm.telemetry.LlmRawStore
import com.vtempe.server.features.ai.data.resolver.DefaultTrainingPlanResolver
import com.vtempe.server.features.ai.data.service.AiService
import com.vtempe.server.features.ai.data.service.ChatService
import com.vtempe.server.features.ai.domain.usecase.BootstrapUseCase
import com.vtempe.server.features.ai.domain.usecase.ChatUseCase
import com.vtempe.server.features.ai.domain.usecase.NutritionUseCase
import com.vtempe.server.features.ai.domain.usecase.SleepUseCase
import com.vtempe.server.features.ai.domain.usecase.TrainingUseCase
import com.vtempe.server.features.auth.IdTokenVerifier
import com.vtempe.server.shared.dto.advice.AiAdviceRequest
import com.vtempe.server.shared.dto.advice.AiAdviceResponse
import com.vtempe.server.shared.dto.bootstrap.AiBootstrapRequest
import com.vtempe.server.shared.dto.bootstrap.AiBootstrapResponse
import com.vtempe.server.shared.dto.chat.AiChatMessage
import com.vtempe.server.shared.dto.chat.AiChatRequest
import com.vtempe.server.shared.dto.chat.AiChatResponse
import com.vtempe.server.shared.dto.nutrition.AiNutritionRequest
import com.vtempe.server.shared.dto.nutrition.AiNutritionResponse
import com.vtempe.server.shared.dto.profile.AiProfile
import com.vtempe.server.shared.dto.training.AiSet
import com.vtempe.server.shared.dto.training.AiTrainingRequest
import com.vtempe.server.shared.dto.training.AiTrainingResponse
import com.vtempe.server.shared.dto.training.AiWorkout
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.OutgoingContent
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.install
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.routing.routing
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import io.ktor.utils.io.ByteWriteChannel
import io.ktor.utils.io.writeFully
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import org.koin.ktor.plugin.Koin

/** VT-AUD-010: AI input is bounded before redaction, prompt building and any LLM call. */
class AiInputLimitsTest {

    @AfterEach
    fun tearDown() {
        stopKoin()
    }

    private class Recorder : TrainingUseCase, NutritionUseCase, SleepUseCase, ChatUseCase, BootstrapUseCase {
        val chats = mutableListOf<AiChatRequest>()
        val trainings = mutableListOf<AiTrainingRequest>()
        /** The profile each use case received, by route. */
        val profiles = mutableMapOf<String, AiProfile>()
        var promptTooLarge = false
        override suspend fun execute(req: AiTrainingRequest) = AiTrainingResponse(0, emptyList()).also { trainings += req; profiles["training"] = req.profile }
        override suspend fun execute(req: AiNutritionRequest) = AiNutritionResponse(0, emptyMap()).also { profiles["nutrition"] = req.profile }
        override suspend fun execute(req: AiAdviceRequest) = AiAdviceResponse(emptyList()).also { profiles["sleep"] = req.profile }
        override suspend fun execute(req: AiChatRequest): AiChatResponse {
            if (promptTooLarge) throw PromptTooLargeException()
            return AiChatResponse(reply = "ok").also { chats += req; profiles["chat"] = req.profile }
        }
        override suspend fun execute(req: AiBootstrapRequest) = AiBootstrapResponse().also { profiles["bootstrap"] = req.profile }
    }

    private val json = Json { encodeDefaults = true }
    private val profile = AiProfile(age = 30, sex = "MALE", heightCm = 180, weightKg = 80.0, goal = "MAINTAIN", experienceLevel = 2)

    private fun limitsTest(block: suspend ApplicationTestBuilder.(Recorder) -> Unit) = testApplication {
        val recorder = Recorder()
        val resolver = AiAccessResolver(
            AiAccessConfig(appSecret = SECRET, paidAccessPolicy = PaidAccessPolicy.EVERYONE),
            IdTokenVerifier { null }
        ) { false }
        application {
            install(Koin) {
                modules(module {
                    single<TrainingUseCase> { recorder }
                    single<NutritionUseCase> { recorder }
                    single<SleepUseCase> { recorder }
                    single<ChatUseCase> { recorder }
                    single<BootstrapUseCase> { recorder }
                    single { resolver }
                })
            }
            install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true; encodeDefaults = true }) }
            routing { registerAiRoutes() }
        }
        block(recorder)
    }

    private suspend fun ApplicationTestBuilder.postJson(path: String, body: String) = client.post(path) {
        header("X-App-Token", SECRET)
        contentType(ContentType.Application.Json)
        setBody(body)
    }

    private fun chatBody(last: String, history: List<AiChatMessage> = emptyList()) =
        json.encodeToString(AiChatRequest(profile, history + AiChatMessage("user", last)))

    @Test
    fun `oversized chat message is refused before the use case`() = limitsTest { recorder ->
        val response = postJson("/ai/chat", chatBody("x".repeat(100_000)))
        assertEquals(HttpStatusCode.BadRequest, response.status)
        assertTrue(response.bodyAsText().contains("message_too_long"))

        val tooLong = postJson("/ai/chat", chatBody("я".repeat(AiInputLimits.MAX_CHAT_MESSAGE_CHARS + 1)))
        assertEquals(HttpStatusCode.BadRequest, tooLong.status)
        assertTrue(recorder.chats.isEmpty())

        val atLimit = postJson("/ai/chat", chatBody("я".repeat(AiInputLimits.MAX_CHAT_MESSAGE_CHARS)))
        assertEquals(HttpStatusCode.OK, atLimit.status)
        assertEquals(1, recorder.chats.size)
    }

    @Test
    fun `history and profile reach the use case already cut`() = limitsTest { recorder ->
        val history = List(30) { AiChatMessage(if (it % 2 == 0) "user" else "assistant", "h$it-" + "z".repeat(3_000)) }
        val big = profile.copy(
            injuries = List(100) { "knee " + "q".repeat(1_000) },
            weeklySchedule = (1..500).associate { "day$it" to true } + ("Mon" to true),
        )
        val body = json.encodeToString(AiChatRequest(big, history + AiChatMessage("user", "hello")))
        assertEquals(HttpStatusCode.OK, postJson("/ai/chat", body).status)

        val received = recorder.chats.single()
        assertEquals(AiInputLimits.MAX_HISTORY_MESSAGES + 1, received.messages.size)
        assertEquals("hello", received.messages.last().content)
        assertTrue(received.messages.dropLast(1).all { it.content.length <= AiInputLimits.MAX_HISTORY_MESSAGE_CHARS + 1 })
        assertTrue(received.messages.first().content.startsWith("h22-"), "the most recent turns are kept")
        assertEquals(AiInputLimits.MAX_FREE_TEXT_ITEMS, received.profile.injuries.size)
        assertTrue(received.profile.injuries.all { it.length <= AiInputLimits.MAX_FREE_TEXT_CHARS })
        assertEquals(mapOf("Mon" to true), received.profile.weeklySchedule)
    }

    @Test
    fun `body over the limit is refused with 413, declared or streamed`() = limitsTest { recorder ->
        val huge = json.encodeToString(AiTrainingRequest(profile.copy(healthNotes = listOf("n".repeat(AiInputLimits.MAX_BODY_BYTES))), 0))
        assertEquals(HttpStatusCode.PayloadTooLarge, postJson("/ai/training", huge).status)

        // No Content-Length: the server must stop reading at the limit on its own.
        val streamed = client.post("/ai/training") {
            header("X-App-Token", SECRET)
            contentType(ContentType.Application.Json)
            setBody(object : OutgoingContent.WriteChannelContent() {
                override val contentType = ContentType.Application.Json
                override suspend fun writeTo(channel: ByteWriteChannel) {
                    channel.writeFully(huge.toByteArray())
                }
            })
        }
        assertEquals(HttpStatusCode.PayloadTooLarge, streamed.status)
        assertTrue(recorder.trainings.isEmpty())
    }

    @Test
    fun `unsupported body measurements are clamped before they reach a use case`() = limitsTest { recorder ->
        // Installed app versions had no bounds in their profile form: refusing these would lock
        // those users out of the AI; the values still never reach a calculation unclamped.
        val bad = profile.copy(weightKg = -1.0, age = 0, heightCm = 999)
        val response = postJson("/ai/training", json.encodeToString(AiTrainingRequest(bad, 0)))

        assertEquals(HttpStatusCode.OK, response.status)
        val received = recorder.trainings.single().profile
        assertEquals(Triple(10, 272, 20.0), Triple(received.age, received.heightCm, received.weightKg))
    }

    @Test
    fun `out-of-range measurements are clamped on every AI route, at both ends`() = limitsTest { recorder ->
        for ((bad, expected) in listOf(
            profile.copy(age = 9, heightCm = 49, weightKg = 19.99) to Triple(10, 50, 20.0),
            profile.copy(age = 111, heightCm = 273, weightKg = 400.01) to Triple(110, 272, 400.0),
        )) {
            postJson("/ai/training", json.encodeToString(AiTrainingRequest(bad, 0)))
            postJson("/ai/nutrition", json.encodeToString(AiNutritionRequest(bad, 0)))
            postJson("/ai/sleep", json.encodeToString(AiAdviceRequest(bad)))
            postJson("/ai/chat", json.encodeToString(AiChatRequest(bad, listOf(AiChatMessage("user", "hi")))))
            postJson("/ai/bootstrap", json.encodeToString(AiBootstrapRequest(bad, 0)))

            assertEquals(setOf("training", "nutrition", "sleep", "chat", "bootstrap"), recorder.profiles.keys)
            recorder.profiles.forEach { (route, p) ->
                assertEquals(expected, Triple(p.age, p.heightCm, p.weightKg), route)
            }
            recorder.profiles.clear()
        }
    }

    @Test
    fun `a prompt over the budget answers 413 request_too_large over HTTP`() = limitsTest { recorder ->
        recorder.promptTooLarge = true

        val response = postJson("/ai/chat", chatBody("hi"))

        assertEquals(HttpStatusCode.PayloadTooLarge, response.status)
        assertTrue(response.bodyAsText().contains("request_too_large"))
    }

    @Test
    fun `measurements inside the range pass unchanged`() = limitsTest { recorder ->
        postJson("/ai/training", json.encodeToString(AiTrainingRequest(profile.copy(weightKg = 20.0, age = 110), 0)))

        val received = recorder.trainings.single().profile
        assertEquals(20.0 to 110, received.weightKg to received.age)
    }

    @Test
    fun `malformed json is a 400, not a server error`() = limitsTest { recorder ->
        val response = postJson("/ai/training", "{not json")
        assertEquals(HttpStatusCode.BadRequest, response.status)
        assertTrue(response.bodyAsText().contains("invalid_request"))
        assertTrue(recorder.trainings.isEmpty())
    }

    // ── Prompt budget inside ChatService ─────────────────────────────────────

    private class CountingLlm : LLMClient {
        val prompts = mutableListOf<String>()
        override suspend fun generateJson(prompt: String, schema: ResponseSchema?): String {
            prompts += prompt
            return """{"reply":"ok"}"""
        }
    }

    private fun chatService(llm: LLMClient): ChatService {
        val pipeline = LlmPipeline(
            PipelineConfig(), ResponseExtractor(), JsonSanitizer(), Decoder(Json { ignoreUnknownKeys = true }),
            FeedbackComposer(), LlmRawStore(enabled = false), LlmErrorTracker()
        )
        val catalog = BuiltInExerciseCatalog()
        val resolver = DefaultTrainingPlanResolver(catalog)
        val ai = AiService(llm, llm, LlmRepairer(pipeline), catalog, resolver)
        return ChatService(llm, llm, LlmRepairer(pipeline), ai, catalog, resolver)
    }

    @Test
    fun `a prompt over the budget is refused before any LLM call`() {
        val llm = CountingLlm()
        val service = chatService(llm)
        val hugePlan = AiTrainingResponse(0, List(7) { d ->
            AiWorkout(id = "w$d", label = "x".repeat(20_000), date = "2026-10-0${d + 1}", sets = listOf(AiSet("squat", 8, 80.0, 7.0)))
        })
        assertThrows(PromptTooLargeException::class.java) {
            runBlocking { service.chat(AiChatRequest(profile, listOf(AiChatMessage("user", "hi")), currentTrainingPlan = hugePlan)) }
        }
        assertTrue(llm.prompts.isEmpty())

        runBlocking { service.chat(AiChatRequest(profile, listOf(AiChatMessage("user", "x".repeat(4_000))))) }
        assertTrue(llm.prompts.isNotEmpty())
        assertTrue(llm.prompts.all { it.length <= AiInputLimits.MAX_PROMPT_CHARS })
    }

    @Test
    fun `cutting never splits a surrogate pair`() = limitsTest { recorder ->
        val emoji = "💪" // 💪, two UTF-16 units
        val history = listOf(AiChatMessage("user", "a" + emoji.repeat(300)))
        assertEquals(HttpStatusCode.OK, postJson("/ai/chat", chatBody("ok", history)).status)
        val cut = recorder.chats.single().messages.first().content.removeSuffix("…")
        assertFalse(cut.last().isHighSurrogate())
    }

    private companion object {
        const val SECRET = "test-app-secret"
    }
}
