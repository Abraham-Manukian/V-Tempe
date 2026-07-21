package com.vtempe.server.shared.dto.sync

import kotlinx.serialization.Serializable

/** Wire shape for PUT /me/sync/{domain}. */
@Serializable
data class SyncPushRequest(val payload: String)

/** Wire shape for one domain's entry in GET /me/sync. */
@Serializable
data class SyncBlobResponse(val payload: String, val updatedAt: String)

/** Wire shape for GET /me/sync — every domain currently stored for the caller, keyed by domain
 *  name. A domain absent from the map means the caller has never pushed it. */
@Serializable
data class SyncPullResponse(val domains: Map<String, SyncBlobResponse>)
