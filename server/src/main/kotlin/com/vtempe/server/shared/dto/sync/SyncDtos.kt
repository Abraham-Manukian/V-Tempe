package com.vtempe.server.shared.dto.sync

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

/** Wire shape for PUT /me/sync/{domain}. */
@Serializable
data class SyncPushRequest(val payload: String)

/** Wire shape for PATCH /me/sync/{domain} on a diary domain (VT-AUD-057): what one device changed
 *  since the state it last synced — entries added or changed, keys it removed. */
@Serializable
data class SyncChangesRequest(
    val upserts: Map<String, JsonElement> = emptyMap(),
    val deletes: List<String> = emptyList(),
)

/** Wire shape for one domain's entry in GET /me/sync. */
@Serializable
data class SyncBlobResponse(val payload: String, val updatedAt: String)

/** Wire shape for GET /me/sync — every domain currently stored for the caller, keyed by domain
 *  name. A domain absent from the map means the caller has never pushed it. */
@Serializable
data class SyncPullResponse(val domains: Map<String, SyncBlobResponse>)
