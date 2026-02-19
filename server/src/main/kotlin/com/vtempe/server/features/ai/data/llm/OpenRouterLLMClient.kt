package com.vtempe.server.features.ai.data.llm

import com.vtempe.server.features.ai.data.llm.dto.ChatCompletionRequestDto
import com.vtempe.server.features.ai.data.llm.dto.ChatCompletionResponseDto
import com.vtempe.server.features.ai.data.llm.dto.ChatMessageDto
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.ResponseException
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.client.statement.bodyAsText
import kotlinx.serialization.json.Json
import java.net.Proxy

private const val REQUEST_TIMEOUT_MS = 180_000L
private const val SOCKET_TIMEOUT_MS = 180_000L
private const val CONNECT_TIMEOUT_MS = 20_000L

class OpenRouterLLMClient(
    apiKey: String,
    model: String = DEFAULT_MODEL,
    baseUrl: String? = null,
    temperature: Double? = null,
    siteUrl: String? = null,
    appName: String? = null,
    requestTimeoutMs: Long = REQUEST_TIMEOUT_MS,
    socketTimeoutMs: Long = SOCKET_TIMEOUT_MS,
    connectTimeoutMs: Long = CONNECT_TIMEOUT_MS,
    topP: Double? = null,
    maxTokens: Int? = null,
) : LLMClient {
    private val resolvedBaseUrl = (baseUrl ?: DEFAULT_BASE_URL).trimEnd('/')
    private val resolvedTemperature = temperature ?: DEFAULT_TEMPERATURE
    private val resolvedSiteUrl = siteUrl ?: DEFAULT_SITE_URL
    private val resolvedAppName = appName ?: DEFAULT_APP_NAME
    private val resolvedModel = model
    private val resolvedRequestTimeoutMs = requestTimeoutMs.coerceAtLeast(1_000L)
    private val resolvedSocketTimeoutMs = socketTimeoutMs.coerceAtLeast(1_000L)
    private val resolvedConnectTimeoutMs = connectTimeoutMs.coerceAtLeast(1_000L)
    private val resolvedTopP = topP
    private val resolvedMaxTokens = maxTokens

    private val http = HttpClient(OkHttp) {
        engine {
            config { proxy(Proxy.NO_PROXY) }
        }
        install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
        install(HttpTimeout) {
            requestTimeoutMillis = resolvedRequestTimeoutMs
            socketTimeoutMillis = resolvedSocketTimeoutMs
            connectTimeoutMillis = resolvedConnectTimeoutMs
        }
        defaultRequest {
            bearerAuth(apiKey)
            header("HTTP-Referer", resolvedSiteUrl)
            header("X-Title", resolvedAppName)
            header(HttpHeaders.ContentType, ContentType.Application.Json)
            header(HttpHeaders.UserAgent, "V-Tempe-Server/1.0")
        }
    }

    override suspend fun generateJson(prompt: String): String {
        val body = ChatCompletionRequestDto(
            model = resolvedModel,
            messages = listOf(
                ChatMessageDto(role = "system", content = SYSTEM_PROMPT),
                ChatMessageDto(role = "user", content = prompt)
            ),
            temperature = resolvedTemperature,
            topP = resolvedTopP,
            maxTokens = resolvedMaxTokens
        )
        val response: ChatCompletionResponseDto = try {
            http.post("$resolvedBaseUrl/chat/completions") {
                setBody(body)
            }.body()
        } catch (ex: ResponseException) {
            throw mapResponseException(ex)
        } catch (ex: HttpRequestTimeoutException) {
            throw IllegalStateException("OpenRouter request timed out: ${ex.message}", ex)
        }
        response.error?.let { err ->
            val code = err.codeAsString ?: "unknown"
            throw IllegalStateException("OpenRouter error $code: ${err.message}")
        }
        val content = response.choices.firstOrNull()?.message?.content?.trim()
            ?: error("OpenRouter response did not contain choices")
        if (content.isEmpty()) {
            error("OpenRouter response was empty")
        }
        return content
    }
    private suspend fun mapResponseException(ex: ResponseException): Throwable {
        val status = ex.response.status
        val bodyText = runCatching { ex.response.bodyAsText() }.getOrNull()?.takeIf { it.isNotBlank() }
        val message = buildString {
            append("OpenRouter HTTP ${status.value}")
            if (bodyText != null) {
                append(": ")
                append(bodyText)
            } else {
                ex.message?.let {
                    append(": ")
                    append(it)
                }
            }
        }
        return if (status == HttpStatusCode.TooManyRequests) {
            val retryAfter = parseRetryAfterMillis(ex.response.headers[HttpHeaders.RetryAfter])
            RateLimitException(message, retryAfter, ex)
        } else {
            IllegalStateException(message.ifBlank { "OpenRouter HTTP ${status.value}" }, ex)
        }
    }

    private fun parseRetryAfterMillis(raw: String?): Long? {
        raw ?: return null
        raw.trim().ifEmpty { return null }
        raw.toLongOrNull()?.let { return it * 1000L }
        raw.toDoubleOrNull()?.let { return (it * 1000L).toLong() }
        return null
    }

    companion object {
        private const val DEFAULT_MODEL = "openrouter/auto"
        private const val DEFAULT_BASE_URL = "https://openrouter.ai/api/v1"
        private const val DEFAULT_TEMPERATURE = 0.35
        private const val DEFAULT_SITE_URL = "https://github.com/example/v-tempe"
        private const val DEFAULT_APP_NAME = "V-Tempe"
        private const val SYSTEM_PROMPT =
            "You must reply with a single valid JSON object that exactly matches the user's schema. " +
            "Do not add explanations, markdown, apologies, or text outside the JSON object."
    }
}


