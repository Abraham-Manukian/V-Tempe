package com.vtempe.server.shared.dto.entitlement

import kotlinx.serialization.Serializable

/** Wire shape for GET /me/entitlement. `active` covers both ACTIVE and GRACE server-side status
 *  (the client doesn't need to distinguish them — grace is purely a server-side renewal-retry
 *  window, not a different UX state). */
@Serializable
data class EntitlementResponse(
    val active: Boolean,
    val expiresAt: String?,
    val source: String?
)
