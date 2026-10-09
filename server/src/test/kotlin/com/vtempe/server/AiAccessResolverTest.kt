package com.vtempe.server

import com.vtempe.server.features.ai.access.AiAccessConfig
import com.vtempe.server.features.ai.access.AiAccessDecision
import com.vtempe.server.features.ai.access.AiAccessResolver
import com.vtempe.server.features.ai.access.LlmTier
import com.vtempe.server.features.ai.access.PaidAccessPolicy
import com.vtempe.server.features.ai.access.resolveLlmMode
import com.vtempe.server.features.auth.IdTokenVerifier
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/** Decision matrix of the `/ai` access check (VT-AUD-003, 004, 005). No network: the token
 *  verifier and the entitlement lookup are local fakes. */
class AiAccessResolverTest {

    private val verifier = IdTokenVerifier { token ->
        when (token) {
            "user-token" -> "uid-user"
            "subscriber-token" -> "uid-sub"
            else -> null
        }
    }
    private val entitled = setOf("uid-sub")

    private fun resolver(
        config: AiAccessConfig,
        hasPaidEntitlement: suspend (String) -> Boolean = { it in entitled },
    ) = AiAccessResolver(config, verifier, hasPaidEntitlement)

    private val configured = AiAccessConfig(appSecret = SECRET)

    private fun granted(userId: String?, tier: LlmTier) = AiAccessDecision.Granted(userId, tier)
    private val unauthorized = AiAccessDecision.Denied(HttpStatusCode.Unauthorized, "Unauthorized")

    @Test
    fun `app token is required and compared exactly`() = runBlocking {
        val r = resolver(configured)
        assertEquals(unauthorized, r.resolve(appToken = null, authorization = null))
        assertEquals(unauthorized, r.resolve(appToken = "wrong", authorization = null))
        assertEquals(unauthorized, r.resolve(appToken = "$SECRET ", authorization = null))
        assertEquals(granted(null, LlmTier.FREE), r.resolve(appToken = SECRET, authorization = null))
    }

    @Test
    fun `missing, empty or blank APP_SECRET closes AI instead of opening it`() = runBlocking {
        for (raw in listOf(null, "", "   ")) {
            val config = AiAccessConfig.from { key -> if (key == AiAccessConfig.APP_SECRET) raw else null }
            val decision = resolver(config).resolve(appToken = null, authorization = null)
            assertEquals(HttpStatusCode.ServiceUnavailable, (decision as AiAccessDecision.Denied).status, "secret=<$raw>")
        }
    }

    @Test
    fun `explicit dev flag skips only the app token check, never user authentication`() = runBlocking {
        val r = resolver(AiAccessConfig.from { key -> if (key == AiAccessConfig.ALLOW_MISSING_APP_SECRET) "true" else null })
        assertEquals(granted(null, LlmTier.FREE), r.resolve(appToken = null, authorization = null))
        assertEquals(unauthorized, r.resolve(appToken = null, authorization = "Bearer forged"))
        assertEquals(granted("uid-sub", LlmTier.PAID), r.resolve(appToken = null, authorization = "Bearer subscriber-token"))
    }

    @Test
    fun `a present Authorization header must carry a valid Firebase token`() = runBlocking {
        val r = resolver(configured)
        for (header in listOf("Bearer forged", "Bearer ", "Bearer", "", "Basic dXNlcjpwYXNz", "user-token")) {
            assertEquals(unauthorized, r.resolve(SECRET, header), "header=<$header>")
        }
        assertEquals(granted("uid-user", LlmTier.FREE), r.resolve(SECRET, "Bearer user-token"))
        assertEquals(granted("uid-user", LlmTier.FREE), r.resolve(SECRET, "bearer  user-token"))
    }

    @Test
    fun `guests are rejected when guest access is disabled`() = runBlocking {
        val r = resolver(configured.copy(guestAccessEnabled = false))
        assertEquals(unauthorized, r.resolve(SECRET, authorization = null))
        assertEquals(granted("uid-user", LlmTier.FREE), r.resolve(SECRET, "Bearer user-token"))
    }

    @Test
    fun `paid tier per policy - guest, signed-in, subscriber`() = runBlocking {
        val expected = mapOf(
            PaidAccessPolicy.ENTITLEMENT to listOf(LlmTier.FREE, LlmTier.FREE, LlmTier.PAID),
            PaidAccessPolicy.AUTHENTICATED to listOf(LlmTier.FREE, LlmTier.PAID, LlmTier.PAID),
            PaidAccessPolicy.EVERYONE to listOf(LlmTier.PAID, LlmTier.PAID, LlmTier.PAID),
        )
        for ((policy, tiers) in expected) {
            val r = resolver(configured.copy(paidAccessPolicy = policy))
            val actual = listOf(null, "Bearer user-token", "Bearer subscriber-token")
                .map { (r.resolve(SECRET, it) as AiAccessDecision.Granted).maxTier }
            assertEquals(tiers, actual, "policy=$policy")
        }
    }

    @Test
    fun `failing entitlement backend never grants the paid tier`() = runBlocking {
        val r = resolver(configured) { error("database down") }
        assertEquals(granted("uid-sub", LlmTier.FREE), r.resolve(SECRET, "Bearer subscriber-token"))
    }

    @Test
    fun `unset or unknown paid policy falls back to entitlement`() {
        for (raw in listOf(null, "", "  ", "all", "paid")) {
            val config = AiAccessConfig.from { key -> if (key == AiAccessConfig.PAID_ACCESS) raw else null }
            assertEquals(PaidAccessPolicy.ENTITLEMENT, config.paidAccessPolicy, "raw=<$raw>")
        }
        assertEquals(PaidAccessPolicy.EVERYONE, PaidAccessPolicy.parse(" Everyone "))
    }

    @Test
    fun `client llmMode can only lower the granted tier`() {
        for (requested in listOf(null, "paid", "PAID", "premium", "")) {
            assertEquals("free", resolveLlmMode(requested, LlmTier.FREE), "requested=<$requested>")
            assertEquals("paid", resolveLlmMode(requested, LlmTier.PAID), "requested=<$requested>")
        }
        assertEquals("free", resolveLlmMode(" Free ", LlmTier.PAID))
        assertEquals("free", resolveLlmMode("free", LlmTier.FREE))
    }

    private companion object {
        const val SECRET = "test-app-secret"
    }
}
