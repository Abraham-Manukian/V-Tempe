package com.vtempe.server.app

import com.vtempe.server.features.entitlement.data.db.DatabaseConnector
import com.vtempe.server.features.entitlement.data.db.DatabaseState
import com.vtempe.server.features.entitlement.data.db.StorageUnavailableException
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCallPipeline
import io.ktor.server.application.call
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get

/**
 * A request that needs the configured database while it is unreachable answers 503 — never a
 * success that only reached memory (VT-AUD-079). Install before the routes so it wraps them.
 */
fun Application.installStorageUnavailableResponse() {
    intercept(ApplicationCallPipeline.Plugins) {
        try {
            proceed()
        } catch (unavailable: StorageUnavailableException) {
            call.respond(HttpStatusCode.ServiceUnavailable, mapOf("error" to "storage_unavailable"))
            finish()
        }
    }
}

/** Readiness, unlike `/health` (liveness): 503 while a configured database is not ready. */
fun Route.readinessRoute(connector: DatabaseConnector) {
    get("/ready") {
        if (connector.state() is DatabaseState.Unavailable) {
            call.respond(HttpStatusCode.ServiceUnavailable, mapOf("status" to "storage_unavailable"))
        } else {
            call.respond(HttpStatusCode.OK, mapOf("status" to "ready"))
        }
    }
}
