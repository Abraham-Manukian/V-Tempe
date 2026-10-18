package com.vtempe.shared.data.repo

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.vtempe.shared.data.network.ApiClient
import com.vtempe.shared.data.network.dto.ChatResponse
import com.vtempe.shared.data.sync.inMemorySettings
import com.vtempe.shared.db.AppDatabase
import com.vtempe.shared.domain.model.AiModelMode
import com.vtempe.shared.domain.model.Goal
import com.vtempe.shared.domain.model.Profile
import com.vtempe.shared.domain.model.Sex
import com.vtempe.shared.domain.repository.AiModelPreferences
import com.vtempe.shared.domain.util.DataResult
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/** A cached coach reply stands in only when the server can't answer, never for a refused message. */
class ChatCacheFallbackTest {
    private val profile = Profile("p", 30, Sex.MALE, 180, 80.0, Goal.MAINTAIN, 2)
    private val settings = inMemorySettings()
    private val cache = AiResponseCache(settings).apply { storeChatResponse(ChatResponse(reply = "an earlier answer")) }
    private val db = AppDatabase(JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY).also { AppDatabase.Schema.create(it) })
    private val mode = object : AiModelPreferences {
        override fun getAiModelMode() = AiModelMode.FREE
        override fun setAiModelMode(mode: AiModelMode) = Unit
    }

    private fun repo(status: HttpStatusCode): NetworkChatRepository {
        val engine = MockEngine { respond("""{"error":"x"}""", status, headersOf(HttpHeaders.ContentType, "application/json")) }
        val api = ApiClient(HttpClient(engine) { install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) } }, "http://test")
        return NetworkChatRepository(api, cache, mode, WorkoutProgressStore(settings, db), SleepStore(settings), WeightStore(settings))
    }

    private fun send(status: HttpStatusCode) = runBlocking {
        repo(status).send(profile, emptyList(), "a new question", "en", null, null)
    }

    @Test
    fun aRefusedMessageIsAFailureNotTheEarlierAnswer() {
        for (status in listOf(HttpStatusCode.BadRequest, HttpStatusCode.PayloadTooLarge, HttpStatusCode.Forbidden)) {
            val failure = assertIs<DataResult.Failure>(send(status), "$status")
            assertEquals(status.value, failure.code)
        }
    }

    @Test
    fun aFailingServerStillFallsBackToTheCachedAnswer() {
        val result = assertIs<DataResult.Success<*>>(send(HttpStatusCode.ServiceUnavailable))
        assertTrue(result.fromCache)
    }
}
