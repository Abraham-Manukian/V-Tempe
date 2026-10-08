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

    // The client calls this BEFORE deleting its Firebase user, while the ID token is still valid.
    // 204 on every call, repeats included — deletion is idempotent.
    delete("/me") {
        // Set by the Firebase auth intercept in Application.kt; absent means that
        // intercept already rejected the request with 401 before this handler runs.
        val userId = call.attributes.getOrNull(UserIdKey)
            ?: return@delete call.respond(HttpStatusCode.Unauthorized)

        accountDeletionService.deleteAccountData(userId)
        call.respond(HttpStatusCode.NoContent)
    }
}
