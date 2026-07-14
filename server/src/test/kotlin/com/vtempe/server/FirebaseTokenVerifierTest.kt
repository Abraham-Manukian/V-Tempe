package com.vtempe.server

import com.vtempe.server.features.auth.data.FirebaseTokenVerifier
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class FirebaseTokenVerifierTest {

    @Test
    fun `verify always returns null when FIREBASE_PROJECT_ID is not configured`() = runBlocking {
        val verifier = FirebaseTokenVerifier(projectId = null)
        assertNull(verifier.verify("anything, doesn't matter"))
        assertNull(verifier.verify(""))
    }

    @Test
    fun `verify returns null for a malformed token even when a project id is configured`() = runBlocking {
        // Real signature verification needs a live JWKS fetch (network), out of scope for a
        // unit test — but malformed input must fail closed before any network call happens.
        val verifier = FirebaseTokenVerifier(projectId = "vtempe-test-project")
        assertNull(verifier.verify("not.a.jwt"))
        assertNull(verifier.verify(""))
    }
}
