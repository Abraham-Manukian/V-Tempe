package com.vtempe.server.features.ai.access

import com.vtempe.server.features.auth.IdTokenVerifier
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.CancellationException
import org.slf4j.LoggerFactory
import java.security.MessageDigest

/** The highest LLM tier a caller may use. Wire values match the client's `llmMode`. */
enum class LlmTier(val wireValue: String) { FREE("free"), PAID("paid") }

sealed interface AiAccessDecision {
    /** [userId] is null for an admitted guest. */
    data class Granted(val userId: String?, val maxTier: LlmTier) : AiAccessDecision

    data class Denied(val status: HttpStatusCode, val error: String) : AiAccessDecision
}

/** Bearer token malformed, invalid, expired or for another project — or no token at all while
 *  guest access is off. The caller is never told which. */
private val Unauthorized = AiAccessDecision.Denied(HttpStatusCode.Unauthorized, "Unauthorized")

/**
 * Single decision point for every `/ai` call, run by [AiAccessPlugin] before the request body
 * is read, the cache is consulted or any LLM is invoked:
 *
 * 1. App build token (`X-App-Token`) — closed when APP_SECRET is missing (VT-AUD-005).
 * 2. User session — a present `Authorization` header must carry a valid Firebase ID token
 *    (VT-AUD-003); an absent one is a guest, admitted only while guest access is enabled.
 * 3. LLM tier — derived from [AiAccessConfig.paidAccessPolicy] and the server-side
 *    entitlement, never from the client's `llmMode` (VT-AUD-004).
 */
class AiAccessResolver(
    private val config: AiAccessConfig,
    private val idTokenVerifier: IdTokenVerifier,
    /** Whether the user's entitlement is ACTIVE or GRACE (EntitlementService.isActiveOrGrace). */
    private val hasPaidEntitlement: suspend (userId: String) -> Boolean,
) {
    private val logger = LoggerFactory.getLogger(AiAccessResolver::class.java)
    private val secretBytes = config.appSecret?.toByteArray(Charsets.UTF_8)

    suspend fun resolve(appToken: String?, authorization: String?): AiAccessDecision {
        if (secretBytes == null) {
            if (!config.allowMissingAppSecret) {
                return AiAccessDecision.Denied(HttpStatusCode.ServiceUnavailable, "AI is not configured")
            }
        } else if (!matchesSecret(appToken, secretBytes)) {
            return Unauthorized
        }

        if (authorization == null) {
            if (!config.guestAccessEnabled) return Unauthorized
            val tier = if (config.paidAccessPolicy == PaidAccessPolicy.EVERYONE) LlmTier.PAID else LlmTier.FREE
            return AiAccessDecision.Granted(userId = null, maxTier = tier)
        }

        val idToken = bearerToken(authorization) ?: return Unauthorized
        val userId = idTokenVerifier.verify(idToken) ?: return Unauthorized
        return AiAccessDecision.Granted(userId = userId, maxTier = tierFor(userId))
    }

    private suspend fun tierFor(userId: String): LlmTier = when (config.paidAccessPolicy) {
        PaidAccessPolicy.EVERYONE, PaidAccessPolicy.AUTHENTICATED -> LlmTier.PAID
        PaidAccessPolicy.ENTITLEMENT -> {
            val entitled = try {
                hasPaidEntitlement(userId)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // An unavailable entitlement store must never hand out paid access; the user
                // still gets an answer from the free tier.
                logger.warn("Entitlement check failed, serving free tier: {}", e::class.simpleName)
                false
            }
            if (entitled) LlmTier.PAID else LlmTier.FREE
        }
    }

    private fun matchesSecret(token: String?, secret: ByteArray): Boolean =
        token != null && MessageDigest.isEqual(token.toByteArray(Charsets.UTF_8), secret)

    private fun bearerToken(header: String): String? {
        val parts = header.trim().split(' ', limit = 2)
        if (parts.size != 2 || !parts[0].equals("Bearer", ignoreCase = true)) return null
        return parts[1].trim().takeIf { it.isNotEmpty() }
    }
}

/**
 * The LLM mode actually used for a request: the client may opt down to free, but anything it
 * sends — "paid", null, or an unknown value — is capped at [maxTier].
 */
fun resolveLlmMode(requested: String?, maxTier: LlmTier): String {
    val wantsFree = requested?.trim()?.equals(LlmTier.FREE.wireValue, ignoreCase = true) == true
    return if (maxTier == LlmTier.FREE || wantsFree) LlmTier.FREE.wireValue else LlmTier.PAID.wireValue
}
