package com.vtempe.server.features.sync.api

import com.vtempe.server.features.auth.UserIdKey
import com.vtempe.server.features.sync.data.service.ALLOWED_SYNC_DOMAINS
import com.vtempe.server.features.sync.data.service.MAX_SYNC_PAYLOAD_CHARS
import com.vtempe.server.features.sync.data.service.SyncService
import com.vtempe.server.shared.dto.sync.SyncBlobResponse
import com.vtempe.server.shared.dto.sync.SyncPullResponse
import com.vtempe.server.shared.dto.sync.SyncPushRequest
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.put
import io.ktor.server.routing.route
import org.koin.ktor.ext.inject

fun Route.registerSyncRoutes() {
    val syncService: SyncService by inject()

    route("/me/sync") {
        put("/{domain}") {
            // Set by the Firebase auth intercept in Application.kt; absent means that
            // intercept already rejected the request with 401 before this handler runs.
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

            syncService.push(userId, domain, request.payload)
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
