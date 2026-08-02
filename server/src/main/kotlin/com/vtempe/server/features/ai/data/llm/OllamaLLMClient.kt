package com.vtempe.server.features.ai.data.llm

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

private const val REQUEST_TIMEOUT_MS = 60_000L
private const val SOCKET_TIMEOUT_MS = 60_000L
private const val CONNECT_TIMEOUT_MS = 15_000L

class OllamaLLMClient(
    private val baseUrl: String = System.getenv("OLLAMA_BASE_URL") ?: "http://localhost:11434",
    private val model: String = System.getenv("OLLAMA_MODEL") ?: "llama3.1:8b-instruct-q4_K_M",
) : LLMClient {
    private val http = HttpClient(OkHttp) {
        install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
        install(HttpTimeout) {
            requestTimeoutMillis = REQUEST_TIMEOUT_MS
            socketTimeoutMillis = SOCKET_TIMEOUT_MS
            connectTimeoutMillis = CONNECT_TIMEOUT_MS
        }
    }

    override suspend fun generateJson(prompt: String, schema: com.vtempe.server.features.ai.data.llm.schema.ResponseSchema?): String {
        val req = GenerateRequest(
            model = model,
            prompt = prompt,
            stream = false,
            format = "json",
            options = Options(temperature = 0.2)
        )
        val res: GenerateResponse = http.post("$baseUrl/api/generate") {
            contentType(ContentType.Application.Json)
            setBody(req)
        }.body()
        return res.response
    }
}

@Serializable
private data class GenerateRequest(
    val model: String,
    val prompt: String,
    val stream: Boolean,
    val format: String,
    val options: Options
)

@Serializable
private data class Options(val temperature: Double? = null)

@Serializable
private data class GenerateResponse(val model: String, val created_at: String, val response: String)

