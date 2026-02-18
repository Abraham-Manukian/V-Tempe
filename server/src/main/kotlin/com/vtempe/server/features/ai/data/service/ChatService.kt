package com.vtempe.server.features.ai.data.service

import com.vtempe.server.shared.dto.advice.AiAdviceRequest
import com.vtempe.server.shared.dto.bootstrap.AiBootstrapRequest
import com.vtempe.server.shared.dto.bootstrap.AiBootstrapResponse
import com.vtempe.server.shared.dto.chat.AiChatRequest
import com.vtempe.server.shared.dto.chat.AiChatResponse
import com.vtempe.server.shared.dto.nutrition.AiNutritionRequest
import com.vtempe.server.shared.dto.profile.AiProfile
import com.vtempe.server.shared.dto.training.AiTrainingRequest
import com.vtempe.server.features.ai.data.llm.LLMClient
import com.vtempe.server.features.ai.data.llm.LlmRepairer
import com.vtempe.server.features.ai.data.llm.decode.SchemaValidator
import com.vtempe.server.features.ai.data.llm.pipeline.ResponseExtractor
import java.util.Locale
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import kotlin.collections.buildList
import org.slf4j.LoggerFactory
import org.slf4j.Logger


class ChatService(
    private val llmClient: LLMClient,
    private val llmRepairer: LlmRepairer,
    private val aiService: AiService
) {
    private val logger = LoggerFactory.getLogger(ChatService::class.java)
    private val extractor = ResponseExtractor()

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
    }

    suspend fun chat(req: AiChatRequest): AiChatResponse {
        return runCatching {
            // 1) обязательно объяви prompt
            val prompt = buildChatPrompt(req) // или как у тебя называется

            val rawResponse = llmRepairer.generate(
                logger = logger,
                operation = "chat",
                requestId = requestId,
                timeoutMs = LlmTimeoutMs,
                prompt = basePrompt,
                callModel = { p -> llmClient.generateJson(p) },
                extraction = com.vtempe.server.features.ai.data.llm.pipeline.FirstJsonObjectExtraction(extractor),
                strategy = AiChatResponse.serializer(),
                validator = com.vtempe.server.features.ai.data.llm.pipeline.Validator { resp ->
                    buildList {
                        validateChatResponse(resp)?.let { add(it) } // если String?
                    }
                }
            )
            return normalizeChatResponse(rawResponse, locale)
        }.getOrElse {
            logger.warn("LLM chat fallback triggered", it)
            AiChatResponse(
                reply = fallbackChatMessage(safeLocale(req.locale)),
                trainingPlan = null,
                nutritionPlan = null,
                sleepAdvice = null,
            )
        }
    }

    suspend fun bootstrap(req: AiBootstrapRequest): AiBootstrapResponse = runCatching {
        aiService.bundle(req)
    }.getOrElse {
        logger.warn("Bootstrap bundle failed", it)
        AiBootstrapResponse(
            trainingPlan = aiService.training(
                AiTrainingRequest(
                    req.profile,
                    req.weekIndex
                )
            ),
            nutritionPlan = aiService.nutrition(
                AiNutritionRequest(
                    req.profile,
                    req.weekIndex
                )
            ),
            sleepAdvice = aiService.sleep(AiAdviceRequest(req.profile))
        )
    }

    private fun extractChatTextSignals(response: AiChatResponse): List<String> = buildList<String> {
        add(response.reply)

        response.trainingPlan?.let { plan ->
            addAll(
                plan.workouts.flatMap { workout ->
                    buildList<String> {
                        add(workout.id)
                        add(workout.date)
                        addAll(workout.sets.map { set -> "${set.exerciseId}:${set.reps}" })
                    }
                }
            )
        }

        response.nutritionPlan?.let { plan ->
            addAll(
                plan.mealsByDay.values.flatten().flatMap { meal ->
                    buildList<String> {
                        add(meal.name)
                        addAll(meal.ingredients)
                    }
                }
            )
            addAll(plan.shoppingList)
        }

        response.sleepAdvice?.let { advice ->
            addAll(advice.messages)
            advice.disclaimer?.let { add(it) }
        }
    }


    private fun buildChatPrompt(
        localeTag: String,
        languageDisplay: String,
        profileJson: String,
        profileSummary: String,
        lastUserMessage: String,
        history: String
    ): String = buildString {
        appendLine("You are a professional AI strength coach, nutritionist, and recovery expert guiding the same athlete long-term.")
        appendLine("User locale: $languageDisplay ($localeTag). Reply in that language and measurement system.")
        appendLine()
        appendLine("PROFILE CONTEXT (JSON):")
        appendLine(profileJson)
        appendLine()
        appendLine("KEY FACTS:")
        append(profileSummary)
        appendLine()
        appendLine("When replying: first acknowledge the latest user message, then provide clear next steps. Only update trainingPlan, nutritionPlan, or sleepAdvice when the user explicitly requests changes or new plans; otherwise return null for unchanged sections.")
        appendLine("Return STRICT JSON matching this schema (no comments or extra text):")
        appendLine("{\"reply\": String,")
        appendLine(" \"trainingPlan\": {\"weekIndex\": Int, \"workouts\": [{ \"id\": String, \"date\": String(YYYY-MM-DD), \"sets\": [{ \"exerciseId\": String, \"reps\": Int, \"weightKg\": Double?, \"rpe\": Double? }] }] } | null,")
        appendLine(" \"nutritionPlan\": {\"weekIndex\": Int, \"mealsByDay\": { DayLabel: [{ \"name\": String, \"ingredients\": [String], \"kcal\": Int, \"macros\": { \"proteinGrams\": Int, \"fatGrams\": Int, \"carbsGrams\": Int, \"kcal\": Int } }] }, \"shoppingList\": [String]} | null,")
        appendLine(" \"sleepAdvice\": {\"messages\": [String], \"disclaimer\": String?} | null }")
        appendLine()
        appendLine("Guidelines:")
        appendLine("- Set plan sections to null when no update is required; never return empty arrays to signal no change.")
        appendLine("- Nutrition updates MUST include integer macros fields in every meal object. Example: {\"name\":\"Power Oats\",\"ingredients\":[\"rolled oats\",\"milk\",\"berries\"],\"kcal\":420,\"macros\":{\"proteinGrams\":35,\"fatGrams\":12,\"carbsGrams\":55,\"kcal\":420}}")
        appendLine("- Ensure macros.kcal equals proteinGrams*4 + carbsGrams*4 + fatGrams*9 (+/- 20 kcal). Fix kcal rather than omitting fields.")
        appendLine("Do not include trailing commas or comments; output must be valid JSON.")
        appendLine("Latest user message: \"$lastUserMessage\".")
        appendLine("Conversation so far:")
        if (history.isNotBlank()) {
            appendLine(history)
        } else {
            appendLine("No previous messages provided.")
        }
    }

    private fun withChatFeedback(basePrompt: String, attempt: Int, feedback: String?): String = buildString {
        append(basePrompt)
        if (feedback != null) {
            appendLine()
            appendLine("Previous attempt issue (#${attempt - 1}): $feedback")
            appendLine("Return only corrected JSON that satisfies the schema.")
        }
    }

    companion object {
        private const val LlmTimeoutMs = 90_000L
        private const val DefaultLocale = "en-US"
        private val logger = LoggerFactory.getLogger(ChatService::class.java)
    }
}

private fun buildChatProfileSummary(profile: AiProfile): String = buildString {
    val weightFormatted = String.format(Locale.US, "%.1f", profile.weightKg)
    appendLine("- Demographics: ${profile.age} y/o ${profile.sex.lowercase(Locale.US)} | ${profile.heightCm} cm | $weightFormatted kg")
    appendLine("- Goal: ${profile.goal}")
    appendLine("- Experience level (1-5): ${profile.experienceLevel}")
    val equipment = if (profile.equipment.isNotEmpty()) profile.equipment.joinToString(", ") else "bodyweight only"
    appendLine("- Available equipment: $equipment")
    if (profile.injuries.isNotEmpty()) appendLine("- Injuries / limitations: ${profile.injuries.joinToString(", ")}")
    if (profile.healthNotes.isNotEmpty()) appendLine("- Contraindications: ${profile.healthNotes.joinToString(", ")}")
    if (profile.weeklySchedule.isNotEmpty()) {
        val available = profile.weeklySchedule.filterValues { it }.keys
        if (available.isNotEmpty()) appendLine("- Preferred training days: ${available.joinToString(", ")}")
    }
    if (profile.dietaryPreferences.isNotEmpty()) appendLine("- Dietary preferences: ${profile.dietaryPreferences.joinToString(", ")}")
    if (profile.allergies.isNotEmpty()) appendLine("- Allergies: ${profile.allergies.joinToString(", ")}")
    append("- Nutrition budget level (1 low .. 3 high): ${profile.budgetLevel}")
}



private fun fallbackChatMessage(locale: Locale): String =
    if (locale.language.equals("ru", ignoreCase = true)) {
        "\u0422\u0440\u0435\u043d\u0435\u0440 \u0432\u0440\u0435\u043c\u0435\u043d\u043d\u043e \u043d\u0435\u0434\u043e\u0441\u0442\u0443\u043f\u0435\u043d. \u041f\u043e\u0436\u0430\u043b\u0443\u0439\u0441\u0442\u0430, \u043f\u043e\u043f\u0440\u043e\u0431\u0443\u0439\u0442\u0435 \u0435\u0449\u0451 \u0440\u0430\u0437 \u0447\u0443\u0442\u044c \u043f\u043e\u0437\u0436\u0435."
    } else {
        "Coach is temporarily unavailable. Please try again later."
    }

internal fun validateChatResponse(response: AiChatResponse): String? {
    if (response.reply.isBlank()) return "reply must contain user-facing text"
    response.trainingPlan?.let { plan ->
        validateTrainingPlan(plan)?.let { return "trainingPlan: $it" }
    }
    response.nutritionPlan?.let { plan ->
        validateNutritionPlan(plan)?.let { return "nutritionPlan: $it" }
    }
    response.sleepAdvice?.let { advice ->
        validateSleepAdvice(advice)?.let { return "sleepAdvice: $it" }
    }
    return null
}

private fun normalizeChatResponse(response: AiChatResponse, locale: Locale): AiChatResponse = response.copy(
    trainingPlan = response.trainingPlan?.let(::normalizeTrainingPlan),
    nutritionPlan = response.nutritionPlan?.let { normalizeNutritionPlan(it, locale) },
    sleepAdvice = response.sleepAdvice?.let(::normalizeAdvice)
)
