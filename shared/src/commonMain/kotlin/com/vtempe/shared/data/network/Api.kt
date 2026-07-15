package com.vtempe.shared.data.network

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.api.createClientPlugin
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logging
import io.ktor.client.plugins.ClientRequestException
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.client.plugins.ServerResponseException
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import io.ktor.http.encodedPath
import io.ktor.serialization.kotlinx.json.json
import com.vtempe.shared.domain.util.DataResult
import com.vtempe.shared.domain.util.DataResult.Reason
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
    ): DataResult<Res> {
        val response = runCatching {
            httpClient.post("$baseUrl$path") {
                contentType(ContentType.Application.Json)
                setBody(body)
            }
        }.getOrElse { throwable -> return mapApiThrowable(throwable) }
        return parseApiResponse<Res>(response)
    }

    suspend inline fun <reified Res : Any> getResult(path: String): DataResult<Res> {
        val response = runCatching {
            httpClient.get("$baseUrl$path")
        }.getOrElse { throwable -> return mapApiThrowable(throwable) }
        return parseApiResponse<Res>(response)
    }

    /** Parses a successful HTTP response into a [DataResult.Success], handling [SerializationException]. */
    @PublishedApi
    internal suspend inline fun <reified Res : Any> parseApiResponse(
        response: HttpResponse
    ): DataResult<Res> {
        val raw = runCatching { response.bodyAsText() }.getOrNull()
        return runCatching {
            val value = if (raw != null) parser.decodeFromString<Res>(raw) else response.body<Res>()
            DataResult.Success(value, rawPayload = raw)
        }.recover { error ->
            when (error) {
                is SerializationException -> {
                    // Primary decode failed — retry once against the payload trimmed to its
                    // outermost {...}, in case the server wrapped the JSON in stray text.
                    val sanitized = raw?.let { sanitizePayload(it) }?.takeIf { it != raw }
                    val repaired = sanitized?.let { runCatching { parser.decodeFromString<Res>(it) } }
                    if (repaired != null && repaired.isSuccess) {
                        DataResult.Success(repaired.getOrThrow(), rawPayload = sanitized)
                    } else {
                        DataResult.Failure(
                            reason = Reason.InvalidFormat,
                            message = error.message,
                            throwable = error,
                            rawPayload = raw
                        )
                    }
                }
                else -> throw error
            }
        }.getOrThrow()
    }

    /** Maps a network/HTTP [Throwable] to the appropriate [DataResult.Failure]. Re-throws [CancellationException]. */
    @PublishedApi
    internal suspend fun mapApiThrowable(throwable: Throwable): DataResult<Nothing> = when (throwable) {
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

/**
 * @param bearerTokenProvider Supplies a fresh Firebase ID token per request (the provider owns
 *   caching/refresh — typically [com.vtempe.shared.domain.repository.AuthRepository.idToken]).
 *   Only attached to requests under /me or /ai. Deliberately NOT Ktor's built-in `Auth`
 *   plugin — that plugin caches the token itself, which fights Firebase's own hourly refresh;
 *   here the provider is re-invoked on every matching request instead.
 */
fun createHttpClient(
    appToken: String? = null,
    bearerTokenProvider: (suspend () -> String?)? = null
) = HttpClient {
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
    if (appToken != null) {
        defaultRequest {
            header("X-App-Token", appToken)
        }
    }
    if (bearerTokenProvider != null) {
        install(createClientPlugin("BearerAuth") {
            onRequest { request, _ ->
                val path = request.url.encodedPath
                if ((path.startsWith("/me/") || path.startsWith("/ai/")) &&
                    request.headers[HttpHeaders.Authorization] == null
                ) {
                    // Never let a missing/failed token block the request — an anonymous call
                    // still goes out, and the server's own 401 is the real error signal.
                    // Cancellation is the one exception: a cancelled coroutine must not go on to
                    // send a request at all, so it's rethrown rather than swallowed here.
                    val token = runCatching { bearerTokenProvider() }
                        .onFailure { if (it is CancellationException) throw it }
                        .getOrNull()
                    if (token != null) {
                        request.headers.append(HttpHeaders.Authorization, "Bearer $token")
                    }
                }
            }
        })
    }
}
