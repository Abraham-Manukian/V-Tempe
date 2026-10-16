package com.vtempe.server

import com.auth0.jwk.Jwk
import com.auth0.jwk.JwkProvider
import com.auth0.jwk.NetworkException
import com.auth0.jwk.SigningKeyNotFoundException
import com.auth0.jwk.UrlJwkProvider
import com.auth0.jwt.JWT
import com.auth0.jwt.JWTCreator
import com.auth0.jwt.algorithms.Algorithm
import com.vtempe.server.features.auth.data.FirebaseTokenVerifier
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.math.BigInteger
import java.nio.file.Path
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.interfaces.RSAPrivateKey
import java.security.interfaces.RSAPublicKey
import java.time.Instant
import java.util.Base64
import kotlin.io.path.writeText

class FirebaseTokenVerifierTest {

    @Test
    fun `verify always returns null when FIREBASE_PROJECT_ID is not configured`() = runBlocking {
        val verifier = FirebaseTokenVerifier(projectId = null)
        assertNull(verifier.verify("anything, doesn't matter"))
        assertNull(verifier.verify(""))
    }

    @Test
    fun `verify returns null for a malformed token even when a project id is configured`() = runBlocking {
        // Uses the production key source (Google's JWKS endpoint): malformed input must fail
        // closed before any network call happens.
        val verifier = FirebaseTokenVerifier(projectId = "vtempe-test-project")
        assertNull(verifier.verify("not.a.jwt"))
        assertNull(verifier.verify(""))
    }

    // The tests below use local signing keys instead of Google's JWKS endpoint, so they pin the
    // signature and claim checks of the JWT library without any network access.

    @Test
    fun `a token signed by the project key with the project claims yields its uid`() = runBlocking {
        assertEquals("uid-1", verifier().verify(token()))
    }

    @Test
    fun `a token issued for another project audience is rejected`() = runBlocking {
        assertNull(verifier().verify(token { withAudience("another-project") }))
    }

    @Test
    fun `a token from another issuer is rejected`() = runBlocking {
        assertNull(verifier().verify(token { withIssuer("https://securetoken.google.com/another-project") }))
    }

    @Test
    fun `an expired token is rejected`() = runBlocking {
        val expired = token {
            withIssuedAt(Instant.now().minusSeconds(7200))
            withExpiresAt(Instant.now().minusSeconds(3600))
        }
        assertNull(verifier().verify(expired))
    }

    @Test
    fun `a token signed by a key other than the published one is rejected`() = runBlocking {
        assertNull(verifier().verify(token(signingKeys = otherKeys)))
    }

    @Test
    fun `a token whose key id is not published is rejected`() = runBlocking {
        assertNull(verifier().verify(token(keyId = "unknown-kid")))
    }

    @Test
    fun `an unsigned token is rejected`() = runBlocking {
        assertNull(verifier().verify(claims().sign(Algorithm.none())))
    }

    @Test
    fun `a token signed with HS256 over the public key bytes is rejected`() = runBlocking {
        val confused = claims().sign(Algorithm.HMAC256(projectKeys.public.encoded))
        assertNull(verifier().verify(confused))
    }

    @Test
    fun `a token with an empty subject yields no uid`() = runBlocking {
        assertNull(verifier().verify(token { withSubject("") }))
    }

    @Test
    fun `a token with a blank subject yields no uid`() = runBlocking {
        assertNull(verifier().verify(token { withSubject("   ") }))
    }

    @Test
    fun `a token without a subject claim yields no uid`() = runBlocking {
        assertNull(verifier().verify(token { withSubject(null) }))
    }

    @Test
    fun `a token without a key id is rejected when several keys are published`(@TempDir dir: Path) = runBlocking {
        // Google publishes more than one key, and UrlJwkProvider only guesses the key for a
        // missing kid when the document holds exactly one.
        val jwks = dir.resolve("jwks.json").apply {
            writeText("""{"keys":[${jwkJson(KEY_ID, projectKeys)},${jwkJson("other-key", otherKeys)}]}""")
        }
        val noKid = claims().withKeyId(null)
            .sign(Algorithm.RSA256(projectKeys.public as RSAPublicKey, projectKeys.private as RSAPrivateKey))
        assertNull(FirebaseTokenVerifier(PROJECT_ID, UrlJwkProvider(jwks.toUri().toURL())).verify(noKid))
    }

    @Test
    fun `a token signed with RS512 by the project key is rejected`() = runBlocking {
        val rs512 = claims().sign(Algorithm.RSA512(projectKeys.public as RSAPublicKey, projectKeys.private as RSAPrivateKey))
        assertNull(verifier().verify(rs512))
    }

    @Test
    fun `a token that is not valid yet is rejected`() = runBlocking {
        assertNull(verifier().verify(token { withNotBefore(Instant.now().plusSeconds(600)) }))
    }

    @Test
    fun `a token that expired seconds ago is rejected without leeway`() = runBlocking {
        assertNull(verifier().verify(token { withExpiresAt(Instant.now().minusSeconds(30)) }))
    }

    @Test
    fun `a failing key source rejects the token instead of throwing`() = runBlocking {
        val unreachable = JwkProvider { throw NetworkException("JWKS endpoint unreachable", null) }
        assertNull(FirebaseTokenVerifier(PROJECT_ID, unreachable).verify(token()))
    }

    @Test
    fun `a JWKS document that is not JSON rejects the token`(@TempDir dir: Path) = runBlocking {
        val jwks = dir.resolve("jwks.json").apply { writeText("<html>502 Bad Gateway</html>") }
        assertNull(FirebaseTokenVerifier(PROJECT_ID, UrlJwkProvider(jwks.toUri().toURL())).verify(token()))
    }

    @Test
    fun `an empty JWKS document rejects the token`(@TempDir dir: Path) = runBlocking {
        val jwks = dir.resolve("jwks.json").apply { writeText("""{"keys":[]}""") }
        assertNull(FirebaseTokenVerifier(PROJECT_ID, UrlJwkProvider(jwks.toUri().toURL())).verify(token()))
    }

    @Test
    fun `keys parsed from a JWKS document verify a project token`(@TempDir dir: Path) = runBlocking {
        // Same parser as production (UrlJwkProvider reads Google's JWKS JSON with Jackson), fed
        // from a local file instead of the network.
        val jwks = dir.resolve("jwks.json").apply { writeText("""{"keys":[${jwkJson(KEY_ID, projectKeys)}]}""") }
        val verifier = FirebaseTokenVerifier(PROJECT_ID, UrlJwkProvider(jwks.toUri().toURL()))
        assertEquals("uid-1", verifier.verify(token()))
    }

    private fun verifier() = FirebaseTokenVerifier(PROJECT_ID, publishedKeys)

    private fun claims(keyId: String = KEY_ID): JWTCreator.Builder = JWT.create()
        .withKeyId(keyId)
        .withIssuer("https://securetoken.google.com/$PROJECT_ID")
        .withAudience(PROJECT_ID)
        .withSubject("uid-1")
        .withIssuedAt(Instant.now().minusSeconds(60))
        .withExpiresAt(Instant.now().plusSeconds(3600))

    private fun token(
        keyId: String = KEY_ID,
        signingKeys: KeyPair = projectKeys,
        customize: JWTCreator.Builder.() -> Unit = {},
    ): String = claims(keyId).apply(customize)
        .sign(Algorithm.RSA256(signingKeys.public as RSAPublicKey, signingKeys.private as RSAPrivateKey))

    private companion object {
        const val PROJECT_ID = "vtempe-test-project"
        const val KEY_ID = "project-key"

        val projectKeys: KeyPair = rsaKeys()
        val otherKeys: KeyPair = rsaKeys()

        /** Publishes only [projectKeys] under [KEY_ID], like Google's JWKS document. */
        val publishedKeys = JwkProvider { kid ->
            if (kid != KEY_ID) throw SigningKeyNotFoundException("no key $kid", null)
            val key = projectKeys.public as RSAPublicKey
            Jwk.fromValues(
                mapOf(
                    "kid" to KEY_ID, "kty" to "RSA", "alg" to "RS256", "use" to "sig",
                    "n" to base64Url(key.modulus), "e" to base64Url(key.publicExponent),
                ),
            )
        }

        /** One entry of a JWKS document, in the shape Google publishes. */
        fun jwkJson(kid: String, keys: KeyPair): String {
            val key = keys.public as RSAPublicKey
            return """{"kid":"$kid","kty":"RSA","alg":"RS256","use":"sig",""" +
                """"n":"${base64Url(key.modulus)}","e":"${base64Url(key.publicExponent)}"}"""
        }

        fun rsaKeys(): KeyPair = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()

        /** Unsigned big-endian bytes without a leading sign byte, as RFC 7518 and Google's JWKS encode them. */
        fun base64Url(value: BigInteger): String {
            val bytes = value.toByteArray().let { if (it.size > 1 && it[0] == 0.toByte()) it.copyOfRange(1, it.size) else it }
            return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
        }
    }
}
