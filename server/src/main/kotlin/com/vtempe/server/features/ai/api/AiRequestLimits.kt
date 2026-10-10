package com.vtempe.server.features.ai.api

import com.vtempe.server.shared.dto.profile.AiProfile
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.request.receiveChannel
import io.ktor.utils.io.core.readText
import io.ktor.utils.io.readRemaining
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

/**
 * Input budget of the AI endpoints (VT-AUD-010). Everything a caller sends is bounded BEFORE
 * any redaction, prompt building or LLM call, so the 30-per-minute rate limit can no longer be
 * outrun by making each request arbitrarily large.
 */
internal object AiRequestLimits {
    /** Whole JSON body; the app's biggest request (chat with 30 messages + both plans) is far below. */
    const val MAX_BODY_BYTES = 512 * 1024

    /** The message the user just typed. The app enforces the same limit in its composer. */
    const val MAX_CHAT_MESSAGE_CHARS = 4_000

    /** Earlier chat turns the prompt keeps, and how much of each. */
    const val MAX_HISTORY_MESSAGES = 8
    const val MAX_HISTORY_MESSAGE_CHARS = 400

    /** User-typed profile lists (injuries, allergies, …): items and characters per item. */
    const val MAX_FREE_TEXT_ITEMS = 30
    const val MAX_FREE_TEXT_CHARS = 300
    const val MAX_NOTE_CHARS = 500

    /** History the client attaches to every request. */
    const val MAX_RECENT_WORKOUTS = 12
    const val MAX_SLEEP_ENTRIES = 31
    const val MAX_WEIGHT_ENTRIES = 60

    /** Final guard on any prompt sent to the provider. */
    const val MAX_PROMPT_CHARS = 120_000

    /** Supported body measurements — technical plausibility, not clinical eligibility. Same as
     *  the app's ProfileBounds (VT-AUD-035). */
    val AGE_YEARS = 10..110
    val HEIGHT_CM = 50..272
    val WEIGHT_KG = 20.0..400.0

    fun hasSupportedMeasurements(profile: AiProfile): Boolean =
        profile.age in AGE_YEARS && profile.heightCm in HEIGHT_CM &&
            profile.weightKg.isFinite() && profile.weightKg in WEIGHT_KG
}

/** A request the AI endpoints refuse to process; answered with [status] and a stable [code]. */
internal class AiInputRejected(val status: HttpStatusCode, val code: String) : RuntimeException(code)

private val boundedJson = Json { ignoreUnknownKeys = true; encodeDefaults = true }

/**
 * Reads and decodes the body with a hard size limit: a declared Content-Length over the limit is
 * refused without reading, and a chunked body stops being read one byte past the limit.
 */
internal suspend inline fun <reified T> ApplicationCall.receiveBounded(maxBytes: Int = AiRequestLimits.MAX_BODY_BYTES): T {
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
