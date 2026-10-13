package com.vtempe.server.features.account.api

import com.vtempe.server.features.account.data.service.AccountDeletionService
import com.vtempe.server.features.auth.UserIdKey
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import org.koin.ktor.ext.inject

fun Route.registerAccountRoutes() {
    val accountDeletionService: AccountDeletionService by inject()

    // DELETE /me (this is mounted on the /me route by registerUserRoutes). The client calls this
    // BEFORE deleting its Firebase user, while the ID token is still valid.
    // 204 on every call, repeats included — deletion is idempotent.
    delete {
        // Set by UserAuthPlugin on the /me subtree (registerUserRoutes), which already answered
        // 401 for a missing or invalid token; absent only if mounted outside it.
        val userId = call.attributes.getOrNull(UserIdKey)
            ?: return@delete call.respond(HttpStatusCode.Unauthorized)

        accountDeletionService.deleteAccountData(userId)
        call.respond(HttpStatusCode.NoContent)
    }
}
