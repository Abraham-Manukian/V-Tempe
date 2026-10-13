package com.vtempe.server.app

import com.vtempe.server.features.account.api.registerAccountRoutes
import com.vtempe.server.features.auth.IdTokenVerifier
import com.vtempe.server.features.auth.UserAuthPlugin
import com.vtempe.server.features.entitlement.api.registerEntitlementRoutes
import com.vtempe.server.features.sync.api.registerSyncRoutes
import io.ktor.server.application.install
import io.ktor.server.routing.Route
import io.ktor.server.routing.route

/** Every per-user route, all under one `/me` subtree that admits only a verified Firebase user. */
fun Route.registerUserRoutes(verifier: IdTokenVerifier) {
    route("/me") {
        install(UserAuthPlugin) { this.verifier = verifier }
        registerAccountRoutes()
        registerEntitlementRoutes()
        registerSyncRoutes()
    }
}
