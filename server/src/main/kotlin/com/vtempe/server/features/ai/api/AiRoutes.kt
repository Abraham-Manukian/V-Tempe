package com.vtempe.server.features.ai.api

import com.vtempe.server.features.ai.access.AiAccessPlugin
import com.vtempe.server.features.ai.access.AiAccessResolver
import com.vtempe.server.features.ai.access.authorizedProfile
import com.vtempe.server.features.ai.data.privacy.boundedForPrompt
import com.vtempe.server.features.ai.data.privacy.latestUserMessage
import com.vtempe.server.features.ai.data.privacy.withSupportedMeasurements
import com.vtempe.server.features.ai.domain.AiInputLimits
import com.vtempe.server.features.ai.domain.PromptTooLargeException
import com.vtempe.server.features.ai.domain.usecase.BootstrapUseCase
import com.vtempe.server.features.ai.domain.usecase.ChatUseCase
import com.vtempe.server.features.ai.domain.usecase.NutritionUseCase
import com.vtempe.server.features.ai.domain.usecase.SleepUseCase
import com.vtempe.server.features.ai.domain.usecase.TrainingUseCase
import com.vtempe.server.shared.dto.advice.AiAdviceRequest
import com.vtempe.server.shared.dto.bootstrap.AiBootstrapRequest
import com.vtempe.server.shared.dto.chat.AiChatRequest
import com.vtempe.server.shared.dto.nutrition.AiNutritionRequest
import com.vtempe.server.shared.dto.profile.AiProfile
import com.vtempe.server.shared.dto.training.AiTrainingRequest
import io.ktor.server.application.call
import io.ktor.server.application.install
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import org.koin.ktor.ext.inject

fun Route.registerAiRoutes() {
    val training: TrainingUseCase by inject()
    val nutrition: NutritionUseCase by inject()
    val sleep: SleepUseCase by inject()
    val chat: ChatUseCase by inject()
    val bootstrap: BootstrapUseCase by inject()
    val accessResolver: AiAccessResolver by inject()

    route("/ai") {
        // Access is enforced on this subtree, not by URI prefix — see AiAccessPlugin. Every
        // handler then reads the body within AiInputLimits, replaces the client's llmMode with
        // the server-resolved tier and bounds every free-text field before any use case runs.
        install(AiAccessPlugin) { resolver = accessResolver }

        post("/training") {
            call.respondAi {
                val req = call.receiveBounded<AiTrainingRequest>()
                training.execute(req.copy(profile = call.authorizedProfile(req.profile.withSupportedMeasurements()).boundedForPrompt()))
            }
        }
        post("/nutrition") {
            call.respondAi {
                val req = call.receiveBounded<AiNutritionRequest>()
                nutrition.execute(req.copy(profile = call.authorizedProfile(req.profile.withSupportedMeasurements()).boundedForPrompt()))
            }
        }
        post("/sleep") {
            call.respondAi {
                val req = call.receiveBounded<AiAdviceRequest>()
                sleep.execute(req.copy(profile = call.authorizedProfile(req.profile.withSupportedMeasurements()).boundedForPrompt()))
            }
        }
        post("/chat") {
            call.respondAi {
                val req = call.receiveBounded<AiChatRequest>()
                // The typed message is refused, not silently cut: the coach must answer what the
                // user actually wrote. The app limits its composer to the same length.
                if (req.latestUserMessage().length > AiInputLimits.MAX_CHAT_MESSAGE_CHARS) {
                    throw AiInputRejected(HttpStatusCode.BadRequest, "message_too_long")
                }
                chat.execute(req.copy(profile = call.authorizedProfile(req.profile.withSupportedMeasurements())).boundedForPrompt())
            }
        }
        post("/bootstrap") {
            call.respondAi {
                val req = call.receiveBounded<AiBootstrapRequest>()
                bootstrap.execute(req.copy(profile = call.authorizedProfile(req.profile.withSupportedMeasurements()).boundedForPrompt()))
            }
        }
    }
}

/** Responds with the handler's result, or with the status and code of a rejected input. */
private suspend inline fun <reified T : Any> ApplicationCall.respondAi(handler: () -> T) {
    val result = try {
        handler()
    } catch (rejected: AiInputRejected) {
        respond(rejected.status, mapOf("error" to rejected.code))
        return
    } catch (tooLarge: PromptTooLargeException) {
        respond(HttpStatusCode.PayloadTooLarge, mapOf("error" to "request_too_large"))
        return
    }
    respond(result)
}
