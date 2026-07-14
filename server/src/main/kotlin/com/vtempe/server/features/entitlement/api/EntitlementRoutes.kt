package com.vtempe.server.features.entitlement.api

import com.vtempe.server.features.auth.UserIdKey
import com.vtempe.server.features.entitlement.data.service.EntitlementService
import com.vtempe.server.features.entitlement.domain.model.EntitlementStatus
import com.vtempe.server.shared.dto.entitlement.EntitlementResponse
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.route
import org.koin.ktor.ext.inject

fun Route.registerEntitlementRoutes() {
    val entitlementService: EntitlementService by inject()

    route("/me") {
        get("/entitlement") {
            // Set by the Firebase auth intercept in Application.kt; absent means that
            // intercept already rejected the request with 401 before this handler runs.
            val userId = call.attributes.getOrNull(UserIdKey)
                ?: return@get call.respond(HttpStatusCode.Unauthorized)

            val entitlement = entitlementService.current(userId)
            call.respond(
                EntitlementResponse(
                    active = entitlement.status != EntitlementStatus.EXPIRED,
                    expiresAt = entitlement.expiresAt?.toString(),
                    source = entitlement.source?.name
                )
            )
        }
    }
}
