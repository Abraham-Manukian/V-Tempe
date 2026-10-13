package com.vtempe.server.shared.http

import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.ApplicationCallPipeline
import io.ktor.server.application.Hook
import io.ktor.server.application.call
import io.ktor.server.application.isHandled

/** Like `onCall`, but stops the pipeline once the handler has responded, so a call a guard
 *  plugin rejected never reaches the route handler. */
object StopWhenHandledHook : Hook<suspend (ApplicationCall) -> Unit> {
    override fun install(pipeline: ApplicationCallPipeline, handler: suspend (ApplicationCall) -> Unit) {
        pipeline.intercept(ApplicationCallPipeline.Plugins) {
            handler(call)
            if (call.isHandled) finish()
        }
    }
}
