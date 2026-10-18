package com.vtempe.server.features.ai.api

import com.vtempe.server.features.ai.domain.AiInputLimits
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.request.receiveChannel
import io.ktor.utils.io.core.readText
import io.ktor.utils.io.readRemaining
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

/** A request the AI endpoints refuse to process; answered with [status] and a stable [code]. */
internal class AiInputRejected(val status: HttpStatusCode, val code: String) : RuntimeException(code)

private val boundedJson = Json { ignoreUnknownKeys = true; encodeDefaults = true }

/**
 * Reads and decodes the body with a hard size limit: a declared Content-Length over the limit is
 * refused without reading, and a chunked body stops being read one byte past the limit.
 */
internal suspend inline fun <reified T> ApplicationCall.receiveBounded(maxBytes: Int = AiInputLimits.MAX_BODY_BYTES): T {
    val declared = request.headers[HttpHeaders.ContentLength]?.toLongOrNull()
    if (declared != null && declared > maxBytes) throw AiInputRejected(HttpStatusCode.PayloadTooLarge, "request_too_large")
    val packet = receiveChannel().readRemaining(maxBytes.toLong() + 1)
    if (packet.remaining > maxBytes) {
        packet.release()
        throw AiInputRejected(HttpStatusCode.PayloadTooLarge, "request_too_large")
    }
    val body = packet.readText()
    return try {
        boundedJson.decodeFromString<T>(body)
    } catch (e: SerializationException) {
        throw AiInputRejected(HttpStatusCode.BadRequest, "invalid_request")
    } catch (e: IllegalArgumentException) {
        throw AiInputRejected(HttpStatusCode.BadRequest, "invalid_request")
    }
}
