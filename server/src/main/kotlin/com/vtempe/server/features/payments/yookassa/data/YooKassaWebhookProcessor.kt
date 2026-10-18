package com.vtempe.server.features.payments.yookassa.data

import com.vtempe.server.features.entitlement.data.service.EntitlementService
import com.vtempe.server.features.entitlement.domain.model.PaymentSource
import com.vtempe.server.features.entitlement.domain.model.VerifiedRefund
import com.vtempe.server.features.entitlement.domain.model.VerifiedPayment
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.slf4j.LoggerFactory
import java.time.Duration

/** How the webhook route must answer YooKassa. */
enum class WebhookReply {
    /** Handled, or nothing a redelivery could change: 200. */
    DONE,
    /** Could not verify with YooKassa right now: a 5xx, so YooKassa delivers again later. */
    RETRY_LATER,
}

/**
 * Handles a YooKassa notification. Trust comes only from re-fetching the object from YooKassa's
 * API ([YooKassaApi]), never from the request body: an attacker who knows a real id could
 * otherwise POST a fake "succeeded" notification and grant themselves access.
 *
 * The body says which kind of object its id names: `refund.succeeded` carries a refund, not a
 * payment (VT-AUD-052) — looking it up as a payment found nothing and the refund was dropped.
 */
class YooKassaWebhookProcessor(
    private val entitlements: EntitlementService,
    private val api: YooKassaApi,
    private val json: Json,
) {
    private val logger = LoggerFactory.getLogger("YooKassaWebhook")

    suspend fun handle(rawBody: String): WebhookReply {
        val body = runCatching { json.parseToJsonElement(rawBody).jsonObject }.getOrNull()
        val event = runCatching { body?.get("event")?.jsonPrimitive?.content }.getOrNull()
        val objectId = runCatching { body?.get("object")?.jsonObject?.get("id")?.jsonPrimitive?.content }.getOrNull()
        if (objectId == null) {
            logger.warn("YooKassa webhook: could not parse an object id from the request body")
            // A malformed body is identically malformed on every retry — nothing to redeliver.
            return WebhookReply.DONE
        }
        return try {
            if (event == REFUND_SUCCEEDED) handleRefund(objectId) else handlePayment(objectId)
            WebhookReply.DONE
        } catch (e: YooKassaVerificationUnavailable) {
            // Not the same as YooKassa confirming the object doesn't exist: retry later instead of
            // permanently dropping a real payment or refund made during an outage.
            logger.error("YooKassa webhook: could not verify {} {}: {}", event ?: "payment", objectId, e.message)
            WebhookReply.RETRY_LATER
        }
    }

    private suspend fun handlePayment(paymentId: String) {
        val payment = api.fetchPayment(paymentId)
        if (payment == null || !payment.paid || payment.status != "succeeded") {
            logger.info("YooKassa webhook: payment {} not confirmed as succeeded, ignoring", paymentId)
            return
        }
        val userId = payment.metadata["userId"]
        if (userId.isNullOrBlank()) {
            logger.error("YooKassa webhook: payment {} succeeded but has no metadata.userId, cannot grant", paymentId)
            return
        }
        val period = periodOf(paymentId, payment.metadata["periodDays"])
        // The current expiry is read inside the payment's transaction (VT-AUD-051); the payment
        // observers (referral rewards) run after it on every delivery.
        val expiresAt = entitlements.recordVerifiedPayment(
            VerifiedPayment(
                externalId = payment.id,
                userId = userId,
                source = PaymentSource.YOOKASSA,
                amountMinor = payment.amount.minorUnits(),
                refundedMinor = payment.refundedAmount?.minorUnits() ?: 0,
                currency = payment.amount.currency,
            ),
            rawPayload = payment.toLedgerPayload(),
            period = period,
        )
        if (expiresAt != null) {
            logger.info("YooKassa webhook: granted entitlement to userId={} until={}", userId, expiresAt)
        } else {
            logger.info("YooKassa webhook: payment {} already processed, skipping duplicate delivery", paymentId)
        }

    }

    private suspend fun handleRefund(refundId: String) {
        val refund = api.fetchRefund(refundId)
        if (refund == null || refund.status != "succeeded") {
            logger.info("YooKassa webhook: refund {} not confirmed as succeeded, ignoring", refundId)
            return
        }
        val payment = api.fetchPayment(refund.paymentId)
        // Full once everything paid is refunded, by this refund alone or together with earlier ones.
        val full = payment != null &&
            (payment.refundedAmount ?: refund.amount).minorUnits() >= payment.amount.minorUnits()
        val result = entitlements.applyRefund(
            VerifiedRefund(
                refundId = refund.id,
                paymentExternalId = refund.paymentId,
                amountMinor = refund.amount.minorUnits(),
                currency = refund.amount.currency,
                full = full,
                rawPayload = json.encodeToString(YooKassaRefund.serializer(), refund),
                paymentRefundedMinor = payment?.refundedAmount?.minorUnits() ?: 0,
            )
        )
        logger.info(
            "YooKassa webhook: refund {} of payment {} -> {} (userId={}, removed={})",
            refund.id, refund.paymentId, result.outcome, result.userId, result.removed,
        )
    }

    /**
     * An amount YooKassa returned but that is not a decimal number is not read as zero: zero would
     * make any refund look "full" and record a free payment. It is a verification failure, so the
     * notification is retried and the error is visible in the logs.
     */
    private fun YooKassaAmount.minorUnits(): Long =
        value.toBigDecimalOrNull()?.movePointRight(2)?.let { runCatching { it.longValueExact() }.getOrNull() }
            ?: throw YooKassaVerificationUnavailable("unreadable amount from YooKassa")

    /** The period a payment bought. Out-of-range metadata falls back to the default instead of
     *  overflowing the expiry (an endless 500 / redelivery loop) or granting years of access. */
    private fun periodOf(paymentId: String, raw: String?): Duration {
        val days = raw?.toLongOrNull() ?: return DEFAULT_PERIOD
        if (days !in PERIOD_DAYS) {
            logger.error("YooKassa webhook: payment {} has periodDays outside {}, using the default", paymentId, PERIOD_DAYS)
            return DEFAULT_PERIOD
        }
        return Duration.ofDays(days)
    }

    private companion object {
        const val REFUND_SUCCEEDED = "refund.succeeded"
        val DEFAULT_PERIOD: Duration = Duration.ofDays(30)
        val PERIOD_DAYS = 1L..366L
    }
}
