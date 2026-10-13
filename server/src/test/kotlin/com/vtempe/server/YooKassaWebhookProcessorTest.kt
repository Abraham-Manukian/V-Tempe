package com.vtempe.server

import com.vtempe.server.features.entitlement.data.repo.InMemoryEntitlementRepository
import com.vtempe.server.features.entitlement.data.service.EntitlementService
import com.vtempe.server.features.payments.yookassa.data.WebhookReply
import com.vtempe.server.features.payments.yookassa.data.YooKassaAmount
import com.vtempe.server.features.payments.yookassa.data.YooKassaApi
import com.vtempe.server.features.payments.yookassa.data.YooKassaPayment
import com.vtempe.server.features.payments.yookassa.data.YooKassaRefund
import com.vtempe.server.features.payments.yookassa.data.YooKassaVerificationUnavailable
import com.vtempe.server.features.payments.yookassa.data.YooKassaWebhookProcessor
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.Duration
import java.time.Instant

/** VT-AUD-052: a `refund.succeeded` notification names a refund, verified with YooKassa, not a payment. */
class YooKassaWebhookProcessorTest {
    private val paymentId = "2d3f5a4b-000f-5000-9000-1b2c3d4e5f60"
    private val refundId = "216749f7-0016-50be-b000-078d43a63ae4"
    private var now = Instant.parse("2026-01-01T00:00:00Z")
    private val entitlements = EntitlementService(InMemoryEntitlementRepository()) { now }

    private class FakeYooKassa : YooKassaApi {
        val payments = mutableMapOf<String, YooKassaPayment>()
        val refunds = mutableMapOf<String, YooKassaRefund>()
        var down = false
        override suspend fun fetchPayment(paymentId: String) = check().let { payments[paymentId] }
        override suspend fun fetchRefund(refundId: String) = check().let { refunds[refundId] }
        private fun check() { if (down) throw YooKassaVerificationUnavailable("outage") }
    }

    private val yooKassa = FakeYooKassa()
    private val processor = YooKassaWebhookProcessor(entitlements, yooKassa, Json { ignoreUnknownKeys = true })

    private fun notification(event: String, id: String) = """{"type":"notification","event":"$event","object":{"id":"$id"}}"""
    private fun payment(refunded: String? = null) = YooKassaPayment(
        id = paymentId, status = "succeeded", paid = true, amount = YooKassaAmount("299.00", "RUB"),
        metadata = mapOf("userId" to "anna", "periodDays" to "30"),
        refundedAmount = refunded?.let { YooKassaAmount(it, "RUB") },
    )
    private fun refund(amount: String, status: String = "succeeded") =
        YooKassaRefund(refundId, status, paymentId, YooKassaAmount(amount, "RUB"))

    private suspend fun paid() {
        yooKassa.payments[paymentId] = payment()
        assertEquals(WebhookReply.DONE, processor.handle(notification("payment.succeeded", paymentId)))
        assertEquals(now.plus(Duration.ofDays(30)), entitlements.current("anna").expiresAt)
        now = now.plus(Duration.ofDays(10))
    }

    @Test
    fun `a full refund takes back the unused part of the payment`() = runBlocking {
        paid()
        yooKassa.payments[paymentId] = payment(refunded = "299.00")
        yooKassa.refunds[refundId] = refund("299.00")

        assertEquals(WebhookReply.DONE, processor.handle(notification("refund.succeeded", refundId)))

        assertEquals(now, entitlements.current("anna").expiresAt)
    }

    @Test
    fun `a partial refund leaves access alone`() = runBlocking {
        paid()
        yooKassa.payments[paymentId] = payment(refunded = "100.00")
        yooKassa.refunds[refundId] = refund("100.00")

        processor.handle(notification("refund.succeeded", refundId))

        assertEquals(now.plus(Duration.ofDays(20)), entitlements.current("anna").expiresAt)
    }

    @Test
    fun `a refund YooKassa does not confirm changes nothing`() = runBlocking {
        paid()
        yooKassa.refunds[refundId] = refund("299.00", status = "pending")

        assertEquals(WebhookReply.DONE, processor.handle(notification("refund.succeeded", refundId)))
        assertEquals(WebhookReply.DONE, processor.handle(notification("refund.succeeded", "deadbeefdeadbeefdeadbeef")))

        assertEquals(now.plus(Duration.ofDays(20)), entitlements.current("anna").expiresAt)
    }

    @Test
    fun `a refund that cannot be verified now is delivered again later`() = runBlocking {
        paid()
        yooKassa.down = true

        assertEquals(WebhookReply.RETRY_LATER, processor.handle(notification("refund.succeeded", refundId)))
    }
}
