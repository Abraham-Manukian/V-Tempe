package com.vtempe.server.features.sync.api

import com.vtempe.server.features.auth.UserIdKey
import com.vtempe.server.features.sync.data.service.ALLOWED_SYNC_DOMAINS
import com.vtempe.server.features.sync.data.service.MAX_SYNC_PAYLOAD_CHARS
import com.vtempe.server.features.sync.data.service.MERGEABLE_SYNC_DOMAINS
import com.vtempe.server.features.sync.data.service.SyncPayloadTooLargeException
import com.vtempe.server.features.sync.data.service.SyncService
import com.vtempe.server.shared.dto.sync.SyncBlobResponse
import com.vtempe.server.shared.dto.sync.SyncChangesRequest
import com.vtempe.server.shared.dto.sync.SyncPullResponse
import com.vtempe.server.shared.dto.sync.SyncPushRequest
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.patch
import io.ktor.server.routing.put
import io.ktor.server.routing.route
import org.koin.ktor.ext.inject

fun Route.registerSyncRoutes() {
    val syncService: SyncService by inject()

    route("/sync") {
        put("/{domain}") {
            // Set by UserAuthPlugin on the /me subtree (registerUserRoutes), which already answered
            // 401 for a missing or invalid token; absent only if mounted outside it.
            val userId = call.attributes.getOrNull(UserIdKey)
                ?: return@put call.respond(HttpStatusCode.Unauthorized)
            val domain = call.parameters["domain"]
            if (domain == null || domain !in ALLOWED_SYNC_DOMAINS) {
                return@put call.respond(HttpStatusCode.BadRequest)
            }

            val request = call.receive<SyncPushRequest>()
            if (request.payload.length > MAX_SYNC_PAYLOAD_CHARS) {
                return@put call.respond(HttpStatusCode.PayloadTooLarge)
            }

            if (!syncService.push(userId, domain, request.payload)) {
                // The account was deleted moments ago; this device's copy must not bring it back.
                return@put call.respond(HttpStatusCode.Gone, mapOf("error" to "account_deleted"))
            }
            call.respond(HttpStatusCode.NoContent)
        }

        // One device's diary changes, merged into the stored diary (VT-AUD-057).
        patch("/{domain}") {
            val userId = call.attributes.getOrNull(UserIdKey)
                ?: return@patch call.respond(HttpStatusCode.Unauthorized)
            val domain = call.parameters["domain"]
            if (domain == null || domain !in MERGEABLE_SYNC_DOMAINS) {
                return@patch call.respond(HttpStatusCode.BadRequest)
            }
            val changes = call.receive<SyncChangesRequest>()
            val merged = try {
                syncService.merge(userId, domain, changes.upserts, changes.deletes)
            } catch (e: SyncPayloadTooLargeException) {
                return@patch call.respond(HttpStatusCode.PayloadTooLarge)
            }
            if (!merged) {
                return@patch call.respond(HttpStatusCode.Gone, mapOf("error" to "account_deleted"))
            }
            call.respond(HttpStatusCode.NoContent)
        }

        get {
            val userId = call.attributes.getOrNull(UserIdKey)
                ?: return@get call.respond(HttpStatusCode.Unauthorized)

            val domains = syncService.pullAll(userId).mapValues { (_, record) ->
                SyncBlobResponse(payload = record.payload, updatedAt = record.updatedAt.toString())
            }
            call.respond(SyncPullResponse(domains))
        }
    }
}
