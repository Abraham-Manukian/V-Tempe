package com.vtempe.server.features.auth

import io.ktor.util.AttributeKey

/** Set by the auth intercept in Application.kt once a Firebase ID token has been verified;
 *  read by any route that needs the caller's identity (entitlement checks, future per-user
 *  data). Never trust a client-supplied userId header directly — always this. */
val UserIdKey: AttributeKey<String> = AttributeKey("userId")
