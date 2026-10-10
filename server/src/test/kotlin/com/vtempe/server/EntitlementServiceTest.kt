package com.vtempe.server

import com.vtempe.server.features.entitlement.data.repo.InMemoryEntitlementRepository
import com.vtempe.server.features.entitlement.data.service.EntitlementService
import com.vtempe.server.features.entitlement.domain.model.EntitlementStatus
import com.vtempe.server.features.entitlement.domain.model.PaymentSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Duration
import java.time.Instant

class EntitlementServiceTest {

    private fun service(now: Instant, grace: Duration = Duration.ofDays(3)) =
        EntitlementService(InMemoryEntitlementRepository(), gracePeriod = grace, clock = { now })

    @Test
    fun `no entitlement record is EXPIRED`() = runBlocking {
        val service = service(now = Instant.parse("2026-01-01T00:00:00Z"))
        val entitlement = service.current("user-1")
        assertEquals(EntitlementStatus.EXPIRED, entitlement.status)
        assertFalse(service.isActiveOrGrace("user-1"))
    }

    @Test
    fun `granted entitlement in the future is ACTIVE`() = runBlocking {
        val now = Instant.parse("2026-01-01T00:00:00Z")
        val service = service(now)
        service.grant("user-1", now.plus(Duration.ofDays(10)), PaymentSource.YOOKASSA)
        val entitlement = service.current("user-1")
        assertEquals(EntitlementStatus.ACTIVE, entitlement.status)
        assertTrue(service.isActiveOrGrace("user-1"))
    }

    @Test
    fun `expiry within the grace window is GRACE, not EXPIRED`() = runBlocking {
        val now = Instant.parse("2026-01-10T00:00:00Z")
        val service = service(now, grace = Duration.ofDays(3))
        // expired 1 day ago, grace period is 3 days -> still within grace
        service.grant("user-1", now.minus(Duration.ofDays(1)), PaymentSource.GOOGLE_PLAY)
        val entitlement = service.current("user-1")
        assertEquals(EntitlementStatus.GRACE, entitlement.status)
        assertTrue(service.isActiveOrGrace("user-1"))
    }

    @Test
    fun `expiry past the grace window is EXPIRED`() = runBlocking {
        val now = Instant.parse("2026-01-10T00:00:00Z")
        val service = service(now, grace = Duration.ofDays(3))
        service.grant("user-1", now.minus(Duration.ofDays(5)), PaymentSource.APP_STORE)
        val entitlement = service.current("user-1")
        assertEquals(EntitlementStatus.EXPIRED, entitlement.status)
        assertFalse(service.isActiveOrGrace("user-1"))
    }

    @Test
    fun `revoke immediately ends access regardless of expiresAt`() = runBlocking {
        val now = Instant.parse("2026-01-01T00:00:00Z")
        val service = service(now)
        service.grant("user-1", now.plus(Duration.ofDays(30)), PaymentSource.YOOKASSA)
        assertTrue(service.isActiveOrGrace("user-1"))
        service.revoke("user-1")
        assertEquals(EntitlementStatus.EXPIRED, service.current("user-1").status)
    }

    @Test
    fun `a renewal webhook never shortens an existing later expiry`() = runBlocking {
        val now = Instant.parse("2026-01-01T00:00:00Z")
        val service = service(now)
        service.grant("user-1", now.plus(Duration.ofDays(30)), PaymentSource.YOOKASSA)
        // Duplicate/out-of-order delivery with an earlier expiry must be a no-op.
        service.grant("user-1", now.plus(Duration.ofDays(5)), PaymentSource.YOOKASSA)
        val entitlement = service.current("user-1")
        assertEquals(now.plus(Duration.ofDays(30)), entitlement.expiresAt)
    }

    @Test
    fun `recordPaymentAndExtend returns null for a duplicate external id and does not re-grant`() = runBlocking {
        val now = Instant.parse("2026-01-01T00:00:00Z")
        val repository = InMemoryEntitlementRepository()
        val service = EntitlementService(repository, clock = { now })
        val expiresAt = now.plus(Duration.ofDays(30))

        val first = service.recordPaymentAndExtend("payment-123", "user-1", PaymentSource.YOOKASSA, 100_00, "RUB", "{}", Duration.ofDays(30))
        val second = service.recordPaymentAndExtend("payment-123", "user-1", PaymentSource.YOOKASSA, 100_00, "RUB", "{}", Duration.ofDays(30))

        assertEquals(expiresAt, first)
        assertNull(second)
        // The duplicate delivery must NOT have extended the grant a second time.
        assertEquals(expiresAt, service.current("user-1").expiresAt)
    }
    @Test
    fun `concurrent payments of one user each add their full period`() = runBlocking {
        val now = Instant.parse("2026-01-01T00:00:00Z")
        val service = EntitlementService(InMemoryEntitlementRepository(), clock = { now })
        service.grant("user-1", now.plus(Duration.ofDays(10)), PaymentSource.YOOKASSA)

        (1..40).map { i ->
            async(Dispatchers.Default) {
                service.recordPaymentAndExtend("payment-$i", "user-1", PaymentSource.YOOKASSA, 100_00, "RUB", "{}", Duration.ofDays(30))
            }
        }.awaitAll()

        assertEquals(now.plus(Duration.ofDays(10 + 40 * 30L)), service.current("user-1").expiresAt)
    }

    @Test
    fun `an expired or missing grant extends from now, a grace one from its expiry`() = runBlocking {
        val now = Instant.parse("2026-01-01T00:00:00Z")
        val service = EntitlementService(InMemoryEntitlementRepository(), gracePeriod = Duration.ofDays(3), clock = { now })
        val period = Duration.ofDays(30)

        assertEquals(now.plus(period), service.recordPaymentAndExtend("p-new", "new", PaymentSource.YOOKASSA, 1, "RUB", "{}", period))

        service.grant("expired", now.minus(Duration.ofDays(10)), PaymentSource.YOOKASSA)
        assertEquals(now.plus(period), service.recordPaymentAndExtend("p-exp", "expired", PaymentSource.YOOKASSA, 1, "RUB", "{}", period))

        val graceEnd = now.minus(Duration.ofDays(1))
        service.grant("grace", graceEnd, PaymentSource.YOOKASSA)
        assertEquals(graceEnd.plus(period), service.recordPaymentAndExtend("p-grace", "grace", PaymentSource.YOOKASSA, 1, "RUB", "{}", period))
    }
}
