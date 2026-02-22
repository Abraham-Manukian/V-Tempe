package com.vtempe.shared.data.network

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logging
import io.ktor.client.plugins.ClientRequestException
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.client.plugins.ServerResponseException
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import com.vtempe.shared.domain.util.DataResult
import com.vtempe.shared.domain.util.DataResult.Reason
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.SerializationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.CancellationException
import io.ktor.utils.io.errors.IOException

private const val DEFAULT_REQUEST_TIMEOUT_MS = 180_000L
private const val DEFAULT_SOCKET_TIMEOUT_MS = 180_000L
private const val DEFAULT_CONNECT_TIMEOUT_MS = 15_000L

class ApiClient(val httpClient: HttpClient, val baseUrl: String) {
    @PublishedApi
    internal val parser = Json { ignoreUnknownKeys = true; isLenient = true; encodeDefaults = true }

    suspend inline fun <reified Req : Any, reified Res : Any> post(path: String, body: Req): Res =
        httpClient.post("$baseUrl$path") {
            contentType(ContentType.Application.Json)
            setBody(body)
        }.body()

    suspend inline fun <reified Res : Any> get(path: String): Res =
        httpClient.get("$baseUrl$path").body()

    suspend inline fun <reified Req : Any, reified Res : Any> postResult(
        path: String,
        body: Req
    ): DataResult<Res> = runCatching {
        httpClient.post("$baseUrl$path") {
            contentType(ContentType.Application.Json)
            setBody(body)
        }
    }.fold(
        onSuccess = { response ->
            val raw = runCatching { response.bodyAsText() }.getOrNull()
            val sanitized = raw?.let { sanitizePayload(it) }
            runCatching {
                val value = when {
                    sanitized != null -> parser.decodeFromString<Res>(sanitized)
                    raw != null -> parser.decodeFromString<Res>(raw)
                    else -> response.body<Res>()
                }
                DataResult.Success(value, rawPayload = sanitized ?: raw)
            }.recover { error ->
                when (error) {
                    is SerializationException -> DataResult.Failure(
                        reason = Reason.InvalidFormat,
                        message = error.message,
                        throwable = error,
                        rawPayload = raw
                    )
                    else -> throw error
                }
            }.getOrThrow()
        },
        onFailure = { throwable ->
            when (throwable) {
                is HttpRequestTimeoutException, is TimeoutCancellationException ->
                    DataResult.Failure(reason = Reason.Timeout, message = throwable.message, throwable = throwable)
                is ClientRequestException -> {
                    val raw = runCatching { throwable.response.bodyAsText() }.getOrNull()
                    DataResult.Failure(
                        reason = Reason.Http,
                        message = throwable.message,
                        code = throwable.response.status.value,
                        throwable = throwable,
                        rawPayload = raw
                    )
                }
                is ServerResponseException -> {
                    val raw = runCatching { throwable.response.bodyAsText() }.getOrNull()
                    DataResult.Failure(
                        reason = Reason.Http,
                        message = throwable.message,
                        code = throwable.response.status.value,
                        throwable = throwable,
                        rawPayload = raw
                    )
                }
                is IOException ->
                    DataResult.Failure(reason = Reason.Network, message = throwable.message, throwable = throwable)
                is CancellationException -> throw throwable
                else ->
                    DataResult.Failure(reason = Reason.Unknown, message = throwable.message, throwable = throwable)
            }
        }
    )

    suspend inline fun <reified Res : Any> getResult(path: String): DataResult<Res> = runCatching {
        httpClient.get("$baseUrl$path")
    }.fold(
        onSuccess = { response ->
            val raw = runCatching { response.bodyAsText() }.getOrNull()
            val sanitized = raw?.let { sanitizePayload(it) }
            runCatching {
                val value = when {
                    sanitized != null -> parser.decodeFromString<Res>(sanitized)
                    raw != null -> parser.decodeFromString<Res>(raw)
                    else -> response.body<Res>()
                }
                DataResult.Success(value, rawPayload = sanitized ?: raw)
            }.recover { error ->
                when (error) {
                    is SerializationException -> DataResult.Failure(
                        reason = Reason.InvalidFormat,
                        message = error.message,
                        throwable = error,
                        rawPayload = raw
                    )
                    else -> throw error
                }
            }.getOrThrow()
        },
        onFailure = { throwable ->
            when (throwable) {
                is HttpRequestTimeoutException, is TimeoutCancellationException ->
                    DataResult.Failure(reason = Reason.Timeout, message = throwable.message, throwable = throwable)
                is ClientRequestException -> {
                    val raw = runCatching { throwable.response.bodyAsText() }.getOrNull()
                    DataResult.Failure(
                        reason = Reason.Http,
                        message = throwable.message,
                        code = throwable.response.status.value,
                        throwable = throwable,
                        rawPayload = raw
                    )
                }
                is ServerResponseException -> {
                    val raw = runCatching { throwable.response.bodyAsText() }.getOrNull()
                    DataResult.Failure(
                        reason = Reason.Http,
                        message = throwable.message,
                        code = throwable.response.status.value,
                        throwable = throwable,
                        rawPayload = raw
                    )
                }
                is IOException ->
                    DataResult.Failure(reason = Reason.Network, message = throwable.message, throwable = throwable)
                is CancellationException -> throw throwable
                else ->
                    DataResult.Failure(reason = Reason.Unknown, message = throwable.message, throwable = throwable)
            }
        }
    )

    @Suppress("NOTHING_TO_INLINE")
    @PublishedApi
    internal inline fun sanitizePayload(raw: String): String? {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return trimmed
        val firstBrace = trimmed.indexOf('{')
        val lastBrace = trimmed.lastIndexOf('}')
        return if (firstBrace >= 0 && lastBrace > firstBrace) {
            trimmed.substring(firstBrace, lastBrace + 1)
        } else {
            trimmed
        }
    }
}

fun createHttpClient() = HttpClient {
    install(ContentNegotiation) {
        json(Json { ignoreUnknownKeys = true; isLenient = true; encodeDefaults = true })
    }
    install(HttpTimeout) {
        requestTimeoutMillis = DEFAULT_REQUEST_TIMEOUT_MS
        socketTimeoutMillis = DEFAULT_SOCKET_TIMEOUT_MS
        connectTimeoutMillis = DEFAULT_CONNECT_TIMEOUT_MS
    }
    install(Logging) {
        level = LogLevel.INFO
    }
}

@Serializable
data class SignupRequest(val email: String, val password: String)
@Serializable
data class SignupResponse(val userId: String, val token: String)

