package com.vtempe.shared.data.network.dto

import kotlinx.serialization.Serializable

/** Wire shape for PUT /me/sync/{domain} — field name must match the server's
 *  SyncPushRequest exactly (server/.../shared/dto/sync/SyncDtos.kt). */
@Serializable
data class SyncPushRequestDto(val payload: String)

/** Wire shape for one domain's entry in GET /me/sync — mirrors the server's SyncBlobResponse. */
@Serializable
data class SyncBlobDto(val payload: String, val updatedAt: String)

/** Wire shape for GET /me/sync — mirrors the server's SyncPullResponse. */
@Serializable
data class SyncPullResponseDto(val domains: Map<String, SyncBlobDto> = emptyMap())
