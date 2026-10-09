package com.vtempe.server

import com.vtempe.server.features.ai.access.AiAccessConfig
import com.vtempe.server.features.ai.access.AiAccessResolver
import com.vtempe.server.features.ai.access.PaidAccessPolicy
import com.vtempe.server.features.ai.api.registerAiRoutes
import com.vtempe.server.features.ai.domain.usecase.BootstrapUseCase
import com.vtempe.server.features.ai.domain.usecase.ChatUseCase
import com.vtempe.server.features.ai.domain.usecase.NutritionUseCase
import com.vtempe.server.features.ai.domain.usecase.SleepUseCase
import com.vtempe.server.features.ai.domain.usecase.TrainingUseCase
import com.vtempe.server.features.auth.IdTokenVerifier
import com.vtempe.server.features.entitlement.data.repo.InMemoryEntitlementRepository
import com.vtempe.server.features.entitlement.data.service.EntitlementService
import com.vtempe.server.features.entitlement.domain.model.PaymentSource
import com.vtempe.server.shared.dto.advice.AiAdviceRequest
import com.vtempe.server.shared.dto.advice.AiAdviceResponse
import com.vtempe.server.shared.dto.bootstrap.AiBootstrapRequest
import com.vtempe.server.shared.dto.bootstrap.AiBootstrapResponse
import com.vtempe.server.shared.dto.chat.AiChatRequest
import com.vtempe.server.shared.dto.chat.AiChatResponse
import com.vtempe.server.shared.dto.nutrition.AiNutritionRequest
import com.vtempe.server.shared.dto.nutrition.AiNutritionResponse
import com.vtempe.server.shared.dto.training.AiTrainingRequest
import com.vtempe.server.shared.dto.training.AiTrainingResponse
import io.ktor.client.HttpClient
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.install
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import org.koin.ktor.plugin.Koin
import java.time.Instant
import java.time.temporal.ChronoUnit

/**
 * HTTP-level regression tests for `/ai` access (VT-AUD-002–005) through the real
 * [registerAiRoutes] wiring and Ktor routing. Use cases are fakes that only record what reached
 * them — no LLM, no network, no database.
 */
class AiRoutesAccessTest {

    @AfterEach
    fun tearDown() {
        stopKoin()
    }

    /** Records every call that got past access control, with the llmMode the handler passed on. */
    private class RecordingAi : TrainingUseCase, NutritionUseCase, SleepUseCase, ChatUseCase, BootstrapUseCase {
        val calls = mutableListOf<Pair<String, String?>>()

        override suspend fun execute(req: AiTrainingRequest): AiTrainingResponse {
            calls += "training" to req.profile.llmMode
            return AiTrainingResponse(weekIndex = req.weekIndex, workouts = emptyList())
        }

        override suspend fun execute(req: AiNutritionRequest): AiNutritionResponse {
            calls += "nutrition" to req.profile.llmMode
            return AiNutritionResponse(weekIndex = req.weekIndex, mealsByDay = emptyMap())
        }

        override suspend fun execute(req: AiAdviceRequest): AiAdviceResponse {
            calls += "sleep" to req.profile.llmMode
            return AiAdviceResponse(messages = emptyList())
        }

        override suspend fun execute(req: AiChatRequest): AiChatResponse {
            calls += "chat" to req.profile.llmMode
            return AiChatResponse(reply = "ok")
        }

        override suspend fun execute(req: AiBootstrapRequest): AiBootstrapResponse {
            calls += "bootstrap" to req.profile.llmMode
            return AiBootstrapResponse()
        }
    }

    private val verifier = IdTokenVerifier { token ->
        when (token) {
            "user-token" -> "uid-user"
            "subscriber-token" -> "uid-sub"
            else -> null
        }
    }

    private val entitlements = EntitlementService(InMemoryEntitlementRepository()).also {
        runBlocking {
            it.grant("uid-sub", Instant.now().plus(30, ChronoUnit.DAYS), PaymentSource.GOOGLE_PLAY)
        }
    }

    private fun aiTest(
        config: AiAccessConfig = AiAccessConfig(appSecret = SECRET),
        block: suspend (client: HttpClient, ai: RecordingAi) -> Unit,
    ) = testApplication {
        val ai = RecordingAi()
        val resolver = AiAccessResolver(config, verifier, entitlements::isActiveOrGrace)
        application {
            install(Koin) {
                modules(module {
                    single<TrainingUseCase> { ai }
                    single<NutritionUseCase> { ai }
                    single<SleepUseCase> { ai }
                    single<ChatUseCase> { ai }
                    single<BootstrapUseCase> { ai }
                    single { resolver }
                })
            }
            install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true; encodeDefaults = true }) }
            routing { registerAiRoutes() }
        }
        block(client, ai)
    }

    private suspend fun HttpClient.callAi(
        path: String,
        endpoint: String = path.substringAfterLast('/').substringBefore('?'),
        appToken: String? = SECRET,
        bearer: String? = null,
        llmMode: String? = "paid",
    ): HttpStatusCode = post(path) {
        appToken?.let { header("X-App-Token", it) }
        bearer?.let { header(HttpHeaders.Authorization, "Bearer $it") }
        contentType(ContentType.Application.Json)
        setBody(body(endpoint, llmMode))
    }.status

    @Test
    fun `every AI route rejects a missing or wrong app token before the use case runs`() = aiTest { client, ai ->
        for (endpoint in ENDPOINTS) {
            assertEquals(HttpStatusCode.Unauthorized, client.callAi("/ai/$endpoint", appToken = null), endpoint)
            assertEquals(HttpStatusCode.Unauthorized, client.callAi("/ai/$endpoint", appToken = "wrong"), endpoint)
        }
        assertEquals(emptyList<Pair<String, String?>>(), ai.calls)
    }

    @Test
    fun `encoded paths, query strings and extra slashes cannot bypass the app token (VT-AUD-002)`() = aiTest { client, ai ->
        for (endpoint in ENDPOINTS) {
            val variants = listOf(
                "/%61i/$endpoint",
                "/%61%69/$endpoint",
                "/ai/%${endpoint.first().code.toString(16)}${endpoint.drop(1)}",
                "/ai/$endpoint?x=1",
                "/%61i/$endpoint?x=/ai/",
                // Absolute form: a bare "//ai/..." would be read by the client as a host name.
                "http://localhost//ai/$endpoint",
                "/ai//$endpoint",
                "/ai/$endpoint/",
            )
            for (path in variants) {
                val status = client.callAi(path, endpoint = endpoint, appToken = null)
                assertNotEquals(HttpStatusCode.OK, status, path)
            }
        }
        assertEquals(emptyList<Pair<String, String?>>(), ai.calls)
    }

    @Test
    fun `an encoded path with valid credentials is still served normally`() = aiTest { client, ai ->
        assertEquals(HttpStatusCode.OK, client.callAi("/%61i/training", endpoint = "training"))
        assertEquals(listOf("training" to "free"), ai.calls)
    }

    @Test
    fun `invalid or expired Firebase token is rejected on every AI route (VT-AUD-003)`() = aiTest { client, ai ->
        for (endpoint in ENDPOINTS) {
            assertEquals(HttpStatusCode.Unauthorized, client.callAi("/ai/$endpoint", bearer = "expired-or-forged"), endpoint)
        }
        assertEquals(emptyList<Pair<String, String?>>(), ai.calls)
    }

    @Test
    fun `guests are capped to the free tier on every route by default (VT-AUD-004)`() = aiTest { client, ai ->
        for (endpoint in ENDPOINTS) {
            for (mode in listOf("paid", null, "premium")) {
                assertEquals(HttpStatusCode.OK, client.callAi("/ai/$endpoint", llmMode = mode), "$endpoint/$mode")
            }
        }
        assertEquals(ENDPOINTS.size * 3, ai.calls.size)
        assertTrue(ai.calls.all { it.second == "free" }, ai.calls.toString())
    }

    @Test
    fun `only an entitled user reaches the paid tier under the default policy`() = aiTest { client, ai ->
        client.callAi("/ai/chat", bearer = "user-token", llmMode = "paid")
        client.callAi("/ai/chat", bearer = "subscriber-token", llmMode = "paid")
        client.callAi("/ai/chat", bearer = "subscriber-token", llmMode = null)
        client.callAi("/ai/chat", bearer = "subscriber-token", llmMode = "free")
        assertEquals(
            listOf("chat" to "free", "chat" to "paid", "chat" to "paid", "chat" to "free"),
            ai.calls
        )
    }

    @Test
    fun `testing phase policy gives every admitted caller the paid tier`() =
        aiTest(AiAccessConfig(appSecret = SECRET, paidAccessPolicy = PaidAccessPolicy.EVERYONE)) { client, ai ->
            assertEquals(HttpStatusCode.OK, client.callAi("/ai/bootstrap"))
            assertEquals(HttpStatusCode.OK, client.callAi("/ai/bootstrap", bearer = "user-token"))
            assertEquals(HttpStatusCode.OK, client.callAi("/ai/bootstrap", bearer = "user-token", llmMode = "free"))
            assertEquals(HttpStatusCode.Unauthorized, client.callAi("/ai/bootstrap", bearer = "forged"))
            assertEquals(HttpStatusCode.Unauthorized, client.callAi("/ai/bootstrap", appToken = null))
            assertEquals(listOf("bootstrap" to "paid", "bootstrap" to "paid", "bootstrap" to "free"), ai.calls)
        }

    @Test
    fun `missing APP_SECRET closes every AI route (VT-AUD-005)`() =
        aiTest(AiAccessConfig(appSecret = null)) { client, ai ->
            for (endpoint in ENDPOINTS) {
                assertEquals(HttpStatusCode.ServiceUnavailable, client.callAi("/ai/$endpoint", appToken = null), endpoint)
                assertEquals(HttpStatusCode.ServiceUnavailable, client.callAi("/%61i/$endpoint", endpoint = endpoint, appToken = null))
            }
            assertEquals(emptyList<Pair<String, String?>>(), ai.calls)
        }

    @Test
    fun `explicit dev mode without APP_SECRET still enforces user authentication`() =
        aiTest(AiAccessConfig(appSecret = null, allowMissingAppSecret = true)) { client, ai ->
            assertEquals(HttpStatusCode.OK, client.callAi("/ai/sleep", appToken = null))
            assertEquals(HttpStatusCode.Unauthorized, client.callAi("/ai/sleep", appToken = null, bearer = "forged"))
            assertEquals(listOf("sleep" to "free"), ai.calls)
        }

    private companion object {
        const val SECRET = "test-app-secret"
        val ENDPOINTS = listOf("training", "nutrition", "sleep", "chat", "bootstrap")

        fun body(endpoint: String, llmMode: String?): String {
            val mode = llmMode?.let { ",\"llmMode\":\"$it\"" } ?: ""
            val profile = "{\"age\":30,\"sex\":\"MALE\",\"heightCm\":180,\"weightKg\":80.0," +
                "\"goal\":\"MAINTAIN\",\"experienceLevel\":2$mode}"
            return when (endpoint) {
                "chat" -> "{\"profile\":$profile,\"messages\":[{\"role\":\"user\",\"content\":\"hi\"}]}"
                else -> "{\"profile\":$profile,\"weekIndex\":0}"
            }
        }
    }
}
