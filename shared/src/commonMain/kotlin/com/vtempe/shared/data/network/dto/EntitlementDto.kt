package com.vtempe.shared.data.network.dto

import kotlinx.serialization.Serializable

/** Wire shape for GET /me/entitlement — field names must match the server's
 *  EntitlementResponse exactly (server/.../shared/dto/entitlement/EntitlementResponse.kt). */
@Serializable
data class EntitlementDto(
    val active: Boolean = false,
    val expiresAt: String? = null,
    val source: String? = null
)
