package com.vtempe.server.features.payments.yookassa.api

import com.vtempe.server.features.entitlement.data.service.EntitlementService
import com.vtempe.server.features.entitlement.domain.model.EntitlementStatus
import com.vtempe.server.features.entitlement.domain.model.PaymentSource
import com.vtempe.server.features.payments.yookassa.data.YooKassaClient
import com.vtempe.server.features.payments.yookassa.data.YooKassaVerificationUnavailable
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.request.receiveText
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.koin.ktor.ext.inject
import org.slf4j.LoggerFactory
import java.math.BigDecimal
import java.time.Duration
import java.time.Instant

private val logger = LoggerFactory.getLogger("YooKassaWebhook")
private val DEFAULT_PERIOD = Duration.ofDays(30)

fun Route.registerYooKassaWebhookRoutes() {
    val entitlementService: EntitlementService by inject()
    val yooKassaClient: YooKassaClient by inject()
    val json: Json by inject()

    // No APP_SECRET/Firebase auth here — this is called by YooKassa's servers, not our app or
    // our users. Trust comes entirely from re-fetching the payment from YooKassa's API below,
    // never from this request's body: an attacker who knows (or guesses) a real payment id
    // could otherwise POST a fake "succeeded" notification here and grant themselves access.
    post("/webhooks/yookassa") {
        val rawBody = call.receiveText()
        val paymentId = runCatching {
            json.parseToJsonElement(rawBody).jsonObject["object"]?.jsonObject?.get("id")?.jsonPrimitive?.content
        }.getOrNull()

        if (paymentId == null) {
            logger.warn("YooKassa webhook: could not parse a payment id from the request body")
            // 200, not 400: a malformed body will be identically malformed on every retry —
            // there's nothing a redelivery can fix, so don't make YooKassa keep retrying.
            call.respond(HttpStatusCode.OK)
            return@post
        }

        val verifiedPayment = try {
            yooKassaClient.fetchPayment(paymentId)
        } catch (e: YooKassaVerificationUnavailable) {
            // Genuinely couldn't verify (network/API outage, missing credentials, unexpected
            // status) — NOT the same as YooKassa confirming the payment doesn't exist. Respond
            // with a server error so YooKassa's webhook delivery retries later, instead of a 200
            // that would permanently drop a real payment made during a transient outage.
            logger.error("YooKassa webhook: could not verify payment {}: {}", paymentId, e.message)
            call.respond(HttpStatusCode.ServiceUnavailable)
            return@post
        }
        if (verifiedPayment == null || !verifiedPayment.paid || verifiedPayment.status != "succeeded") {
            logger.info("YooKassa webhook: payment {} not confirmed as succeeded, ignoring", paymentId)
            call.respond(HttpStatusCode.OK)
            return@post
        }

        val userId = verifiedPayment.metadata["userId"]
        if (userId.isNullOrBlank()) {
            logger.error("YooKassa webhook: payment {} succeeded but has no metadata.userId, cannot grant", paymentId)
            call.respond(HttpStatusCode.OK)
            return@post
        }

        val amountMinor = runCatching {
            BigDecimal(verifiedPayment.amount.value).movePointRight(2).toLong()
        }.getOrDefault(0L)

        val periodDays = verifiedPayment.metadata["periodDays"]?.toLongOrNull()
        val period = periodDays?.let(Duration::ofDays) ?: DEFAULT_PERIOD
        val current = entitlementService.current(userId)
        val base = if (current.status != EntitlementStatus.EXPIRED) current.expiresAt ?: Instant.now() else Instant.now()
        val expiresAt = base.plus(period)

        val granted = entitlementService.recordPaymentAndGrant(
            externalId = verifiedPayment.id,
            userId = userId,
            source = PaymentSource.YOOKASSA,
            amountMinor = amountMinor,
            currency = verifiedPayment.amount.currency,
            rawPayload = rawBody,
            expiresAt = expiresAt
        )
        if (granted) {
            logger.info("YooKassa webhook: granted entitlement to userId={} until={}", userId, expiresAt)
        } else {
            logger.info("YooKassa webhook: payment {} already processed, skipping duplicate delivery", paymentId)
        }
        call.respond(HttpStatusCode.OK)
    }
}
