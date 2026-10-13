package com.vtempe.shared.data.network.dto

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

/** Wire shape for PUT /me/sync/{domain} — field name must match the server's
 *  SyncPushRequest exactly (server/.../shared/dto/sync/SyncDtos.kt). */
@Serializable
data class SyncPushRequestDto(val payload: String)

/** Wire shape for PATCH /me/sync/{domain} on a diary — mirrors the server's SyncChangesRequest
 *  (VT-AUD-057): what this device changed since its last synced state. */
@Serializable
data class SyncChangesDto(
    val upserts: Map<String, JsonElement> = emptyMap(),
    val deletes: List<String> = emptyList(),
) {
    val isEmpty: Boolean get() = upserts.isEmpty() && deletes.isEmpty()
}

/** Wire shape for one domain's entry in GET /me/sync — mirrors the server's SyncBlobResponse. */
@Serializable
data class SyncBlobDto(val payload: String, val updatedAt: String)

/** Wire shape for GET /me/sync — mirrors the server's SyncPullResponse. */
@Serializable
data class SyncPullResponseDto(val domains: Map<String, SyncBlobDto> = emptyMap())
