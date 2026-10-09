package com.vtempe.server.features.auth

/** Turns a client-supplied ID token into a verified user id. Production implementation:
 *  [com.vtempe.server.features.auth.data.FirebaseTokenVerifier]; tests supply a local fake so
 *  access-control checks never need the network or real signing keys. */
fun interface IdTokenVerifier {
    /** The verified uid, or null for any failure — callers must treat null as unauthenticated. */
    suspend fun verify(idToken: String): String?
}
