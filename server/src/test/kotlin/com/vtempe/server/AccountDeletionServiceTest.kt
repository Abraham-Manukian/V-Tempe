package com.vtempe.server

import com.vtempe.server.features.account.data.service.AccountDeletionService
import com.vtempe.server.features.entitlement.data.repo.InMemoryEntitlementRepository
import com.vtempe.server.features.entitlement.domain.model.PaymentSource
import com.vtempe.server.features.sync.data.repo.InMemorySyncBlobRepository
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Instant

class AccountDeletionServiceTest {

    private val syncBlobs = InMemorySyncBlobRepository()
    private val entitlements = InMemoryEntitlementRepository()
    private val service = AccountDeletionService(syncBlobs, entitlements)
    private val expiresAt = Instant.parse("2027-01-01T00:00:00Z")

    @Test
    fun `deletes the user's synced data and entitlement`() = runBlocking {
        syncBlobs.push("user-1", "profile", """{"age":30}""")
        syncBlobs.push("user-1", "weight", """{"2026-09-01":70.0}""")
        entitlements.grantUntilAtLeast("user-1", expiresAt, PaymentSource.YOOKASSA, "pay-1")

        service.deleteAccountData("user-1")

        assertTrue(syncBlobs.pullAll("user-1").isEmpty())
        assertNull(entitlements.find("user-1"))
    }

    @Test
    fun `leaves other users untouched`() = runBlocking {
        syncBlobs.push("user-1", "profile", "{}")
        syncBlobs.push("user-2", "profile", """{"age":41}""")
        entitlements.grantUntilAtLeast("user-2", expiresAt, PaymentSource.GOOGLE_PLAY, null)

        service.deleteAccountData("user-1")

        assertEquals("""{"age":41}""", syncBlobs.pullAll("user-2")["profile"]?.payload)
        assertNotNull(entitlements.find("user-2"))
    }

    @Test
    fun `is idempotent, including for a user with no data`() = runBlocking {
        syncBlobs.push("user-1", "sleep", "{}")

        service.deleteAccountData("user-1")
        service.deleteAccountData("user-1")
        service.deleteAccountData("never-seen")

        assertTrue(syncBlobs.pullAll("user-1").isEmpty())
    }

    @Test
    fun `payment records are retained`() = runBlocking {
        entitlements.recordPaymentAndGrant(
            externalId = "pay-1",
            userId = "user-1",
            source = PaymentSource.YOOKASSA,
            amountMinor = 49_900,
            currency = "RUB",
            rawPayload = "{}",
            expiresAt = expiresAt
        )

        service.deleteAccountData("user-1")

        assertNull(entitlements.find("user-1"))
        // The ledger still knows the payment: a redelivered webhook is recognised as a duplicate.
        assertFalse(
            entitlements.recordPaymentAndGrant("pay-1", "user-1", PaymentSource.YOOKASSA, 49_900, "RUB", "{}", expiresAt)
        )
    }
}
