package com.vtempe.server

import com.vtempe.server.features.entitlement.data.repo.InMemoryEntitlementRepository
import com.vtempe.server.features.entitlement.data.service.EntitlementService
import com.vtempe.server.features.entitlement.domain.model.PaymentSource
import com.vtempe.server.features.entitlement.domain.model.RefundOutcome
import com.vtempe.server.features.entitlement.domain.model.VerifiedRefund
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.Duration
import java.time.Instant

/**
 * VT-AUD-052, owner's policy: a full refund takes back only the unused part of that payment's
 * period — later paid periods move up and stay whole; a partial refund changes nothing.
 */
class RefundPolicyTest {
    private val month = Duration.ofDays(30)
    private var now = Instant.parse("2026-01-01T00:00:00Z")
    private val service = EntitlementService(InMemoryEntitlementRepository()) { now }

    private suspend fun pay(id: String, user: String = "anna") =
        service.recordPaymentAndExtend(id, user, PaymentSource.YOOKASSA, 299_00, "RUB", "{}", month)

    private suspend fun refund(refundId: String, paymentId: String, full: Boolean = true) =
        service.applyRefund(VerifiedRefund(refundId, paymentId, 299_00, "RUB", full, "{}"))

    private suspend fun expiry(user: String = "anna") = service.current(user).expiresAt

    @Test
    fun `refunding the running month keeps the next paid month whole`() = runBlocking {
        pay("A") // Jan 1 – Jan 31
        pay("B") // Jan 31 – Mar 2
        now = now.plus(Duration.ofDays(19))

        val result = refund("r-A", "A")

        assertEquals(RefundOutcome.ACCESS_REDUCED, result.outcome)
        assertEquals(Duration.ofDays(11), result.removed, "only A's unused 11 days")
        assertEquals(now.plus(month), expiry(), "B's 30 days start now")
    }

    @Test
    fun `a refund after its period was used up takes nothing`() = runBlocking {
        pay("A")
        pay("B")
        now = now.plus(Duration.ofDays(40)) // inside B

        val result = refund("r-A", "A")

        assertEquals(Duration.ZERO, result.removed)
        assertEquals(Instant.parse("2026-03-02T00:00:00Z"), expiry())
    }

    @Test
    fun `refunding the last month ends access now`() = runBlocking {
        pay("A")
        pay("B")
        now = now.plus(Duration.ofDays(40))

        refund("r-B", "B")

        assertEquals(now, expiry())
    }

    @Test
    fun `refunding both months one after the other leaves no access`() = runBlocking {
        pay("A")
        pay("B")
        now = now.plus(Duration.ofDays(5))
        refund("r-A", "A") // B now runs Jan 6 – Feb 5
        now = now.plus(Duration.ofDays(15)) // inside B as moved up

        refund("r-B", "B")

        assertEquals(now, expiry(), "B's window moved up with the first refund, so the second removes exactly its rest")
    }

    @Test
    fun `a partial refund changes no access`() = runBlocking {
        pay("A")
        now = now.plus(Duration.ofDays(5))

        val result = refund("r-A", "A", full = false)

        assertEquals(RefundOutcome.PARTIAL_REVIEW, result.outcome)
        assertEquals(Instant.parse("2026-01-31T00:00:00Z"), expiry())
    }

    @Test
    fun `a redelivered refund applies once`() = runBlocking {
        pay("A")
        pay("B")
        now = now.plus(Duration.ofDays(10))

        refund("r-A", "A")
        val again = refund("r-A", "A")

        assertEquals(RefundOutcome.DUPLICATE, again.outcome)
        assertEquals(now.plus(month), expiry())
    }

    @Test
    fun `a refund of an unknown payment is kept for review and touches nobody`() = runBlocking {
        pay("A")

        val result = refund("r-X", "not-ours")

        assertEquals(RefundOutcome.UNMATCHED_REVIEW, result.outcome)
        assertEquals(Instant.parse("2026-01-31T00:00:00Z"), expiry())
    }

    @Test
    fun `another user's periods are untouched`() = runBlocking {
        pay("A")
        pay("C", user = "boris")
        now = now.plus(Duration.ofDays(10))

        refund("r-A", "A")

        assertEquals(Instant.parse("2026-01-31T00:00:00Z"), expiry("boris"))
    }

    @Test
    fun `a full refund verified before its payment is applied when the payment is recorded`() = runBlocking {
        assertEquals(RefundOutcome.UNMATCHED_REVIEW, refund("r-A", "A").outcome) // payment not recorded yet
        now = now.plus(Duration.ofDays(2))

        pay("A")

        assertEquals(now, expiry(), "the refunded payment keeps no unused access")
        assertEquals(RefundOutcome.DUPLICATE, refund("r-A", "A").outcome, "the redelivered refund changes nothing more")
        assertEquals(now, expiry())
    }

    @Test
    fun `a payment the provider reports as refunded in full grants no unused access`() = runBlocking {
        pay("A")

        val expiresAt = service.recordPaymentAndExtend("B", "anna", PaymentSource.YOOKASSA, 299_00, "RUB", "{}", month, refundedMinor = 299_00)

        assertEquals(Instant.parse("2026-01-31T00:00:00Z"), expiresAt, "only A's month remains")
        assertEquals(expiresAt, expiry())
    }

    @Test
    fun `a partial refund verified before its payment leaves the period whole`() = runBlocking {
        service.applyRefund(VerifiedRefund("r-A", "A", 100_00, "RUB", false, "{}"))

        pay("A")

        assertEquals(now.plus(month), expiry())
    }

    @Test
    fun `partial refunds verified before the payment that add up to it count as full`() = runBlocking {
        service.applyRefund(VerifiedRefund("r-1", "A", 100_00, "RUB", false, "{}"))
        service.applyRefund(VerifiedRefund("r-2", "A", 199_00, "RUB", false, "{}"))

        pay("A")

        assertEquals(now, expiry())
    }
}
