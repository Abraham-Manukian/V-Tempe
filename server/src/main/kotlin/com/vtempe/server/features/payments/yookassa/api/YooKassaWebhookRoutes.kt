package com.vtempe.server.features.payments.yookassa.api

import com.vtempe.server.features.payments.yookassa.data.WebhookReply
import com.vtempe.server.features.payments.yookassa.data.YooKassaWebhookProcessor
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.request.receiveText
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import org.koin.ktor.ext.inject

fun Route.registerYooKassaWebhookRoutes() {
    val processor: YooKassaWebhookProcessor by inject()

    // No APP_SECRET/Firebase auth here — this is called by YooKassa's servers, not our app or
    // our users. Trust comes entirely from re-fetching the object from YooKassa's API (see
    // YooKassaWebhookProcessor), never from this request's body.
    post("/webhooks/yookassa") {
        when (processor.handle(call.receiveText())) {
            WebhookReply.DONE -> call.respond(HttpStatusCode.OK)
            WebhookReply.RETRY_LATER -> call.respond(HttpStatusCode.ServiceUnavailable)
        }
    }
}
