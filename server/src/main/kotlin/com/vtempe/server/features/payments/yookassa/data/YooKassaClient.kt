package com.vtempe.server.features.payments.yookassa.data

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.basicAuth
import io.ktor.client.request.get
import io.ktor.client.statement.HttpResponse
import io.ktor.http.HttpStatusCode
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory

@Serializable
data class YooKassaAmount(val value: String, val currency: String)

@Serializable
data class YooKassaPayment(
    val id: String,
    val status: String,
    val paid: Boolean,
    val amount: YooKassaAmount,
    val metadata: Map<String, String> = emptyMap()
)

/** Thrown when a payment's status genuinely could not be determined (network error, YooKassa
 *  API outage, unexpected HTTP status, missing shop credentials) — distinct from [fetchPayment]
 *  returning null, which means YooKassa positively confirmed no such payment exists (404). The
 *  webhook route must treat these differently: "couldn't verify" should make the provider retry
 *  later (so a transient outage doesn't silently drop a real payment), "confirmed not found"
 *  should not (retrying an id that will never exist wastes both sides' time). */
class YooKassaVerificationUnavailable(message: String, cause: Throwable? = null) : Exception(message, cause)

/**
 * Talks to YooKassa's REST API — used ONLY to re-fetch a payment by id and confirm its real
 * status directly from YooKassa, never to trust a webhook body on its own (see
 * YooKassaWebhookRoutes kdoc for why).
 */
class YooKassaClient(private val shopId: String?, private val secretKey: String?) {
    private val logger = LoggerFactory.getLogger(YooKassaClient::class.java)

    private val http = HttpClient(OkHttp) {
        install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
        install(HttpTimeout) {
            requestTimeoutMillis = 10_000
            socketTimeoutMillis = 10_000
            connectTimeoutMillis = 5_000
        }
    }

    /** Returns null only when YooKassa positively confirms the payment id doesn't exist (404) —
     *  or when [paymentId] doesn't even look like a real YooKassa id (see [PAYMENT_ID_PATTERN]),
     *  in which case we never even build a URL from it. Throws [YooKassaVerificationUnavailable]
     *  for every other failure mode (missing credentials, network error, non-2xx/404 status). */
    suspend fun fetchPayment(paymentId: String): YooKassaPayment? {
        val shopId = this.shopId
        val secretKey = this.secretKey
        if (shopId == null || secretKey == null) {
            throw YooKassaVerificationUnavailable("YOOKASSA_SHOP_ID/YOOKASSA_SECRET_KEY not set")
        }
        if (!PAYMENT_ID_PATTERN.matches(paymentId)) {
            // paymentId is attacker-controlled (parsed from an unauthenticated webhook body) —
            // never interpolate it into a URL unvalidated, it could contain "/", "?", "#" and
            // redirect this authenticated request to an arbitrary path/query on YooKassa's API.
            logger.warn("YooKassa webhook: payment id does not match the expected shape, refusing to look it up: {}", paymentId)
            return null
        }
        return try {
            val response: HttpResponse = http.get("https://api.yookassa.ru/v3/payments/$paymentId") {
                basicAuth(shopId, secretKey)
            }
            when {
                response.status == HttpStatusCode.NotFound -> null
                response.status.isSuccess() -> response.body()
                else -> throw YooKassaVerificationUnavailable("YooKassa lookup failed: id=$paymentId status=${response.status}")
            }
        } catch (e: YooKassaVerificationUnavailable) {
            throw e
        } catch (e: Exception) {
            throw YooKassaVerificationUnavailable("YooKassa lookup error: id=$paymentId error=${e.message}", e)
        }
    }

    private companion object {
        // YooKassa payment ids are UUID-shaped (32 lowercase hex chars, no dashes in practice,
        // but accept dashes too since that's the more common UUID rendering) — this is purely a
        // "does this look like a real id" gate, not a strict format contract.
        val PAYMENT_ID_PATTERN = Regex("^[0-9a-f-]{16,64}$")
    }
}
