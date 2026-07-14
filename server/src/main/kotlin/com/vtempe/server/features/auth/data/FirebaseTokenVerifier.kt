package com.vtempe.server.features.auth.data

import com.auth0.jwk.JwkProviderBuilder
import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.auth0.jwt.exceptions.JWTVerificationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.URI
import java.security.interfaces.RSAPublicKey
import java.util.concurrent.TimeUnit
import org.slf4j.LoggerFactory

/**
 * Verifies Firebase ID tokens (the client SDKs hand these to us as `Authorization: Bearer <jwt>`)
 * without pulling in the full Firebase Admin SDK, which needs a service-account secret. Token
 * verification only needs the project's PUBLIC signing keys plus the expected `aud`/`iss`
 * claims (the project id) — both fetched/checked here, no secret required.
 *
 * Degrades gracefully when [projectId] is null (FIREBASE_PROJECT_ID unset) — mirrors how
 * APP_SECRET being unset leaves the AI routes open rather than crashing the app: [verify]
 * simply always returns null, so every request under /me is rejected as unauthenticated until
 * the owner creates a Firebase project and sets the env var.
 */
class FirebaseTokenVerifier(private val projectId: String?) {
    private val logger = LoggerFactory.getLogger(FirebaseTokenVerifier::class.java)

    private val jwkProvider = projectId?.let {
        // The service-account email in this path contains "@" — java.net.URI's strict RFC 3986
        // parser rejects the literal character here (even though "@" is a valid pchar), so it
        // must be percent-encoded before constructing the URI.
        JwkProviderBuilder(URI("https://www.googleapis.com/service_accounts/v1/jwk/securetoken%40system.gserviceaccount.com").toURL())
            .cached(10, 1, TimeUnit.HOURS)
            .rateLimited(10, 1, TimeUnit.MINUTES)
            .build()
    }

    /** Returns the Firebase `uid` if [idToken] is a valid, unexpired token issued for this
     *  project; null on any failure (missing config, expired, wrong project/audience, bad
     *  signature, malformed token) — callers treat every failure mode identically as
     *  "unauthenticated", never distinguishing why.
     *
     *  suspend + Dispatchers.IO: on a JWKS cache miss (rare — cached for 10h), the underlying
     *  jwks-rsa provider does a synchronous HTTP fetch; calling this directly from a Ktor
     *  request-pipeline coroutine would otherwise block a Netty event-loop thread. */
    suspend fun verify(idToken: String): String? {
        val projectId = this.projectId ?: return null
        val provider = jwkProvider ?: return null
        return withContext(Dispatchers.IO) {
            try {
                val unverified = JWT.decode(idToken)
                val jwk = provider.get(unverified.keyId)
                val algorithm = Algorithm.RSA256(jwk.publicKey as RSAPublicKey, null)
                val verifier = JWT.require(algorithm)
                    .withIssuer("https://securetoken.google.com/$projectId")
                    .withAudience(projectId)
                    .build()
                val decoded = verifier.verify(idToken)
                decoded.subject?.takeIf { it.isNotBlank() }
            } catch (e: JWTVerificationException) {
                logger.debug("Firebase ID token failed verification: {}", e.message)
                null
            } catch (e: Exception) {
                logger.debug("Firebase ID token verification error: {}", e.message)
                null
            }
        }
    }
}
