package com.vtempe.server.features.ai.data.service

import com.vtempe.server.features.ai.data.llm.LLMClient
import com.vtempe.server.features.ai.data.llm.LlmRepairer
import com.vtempe.server.features.ai.data.llm.decode.SchemaValidator
import com.vtempe.server.features.ai.data.llm.pipeline.ExtractionMode
import com.vtempe.server.config.Env
import com.vtempe.server.shared.dto.advice.AiAdviceRequest
import com.vtempe.server.shared.dto.bootstrap.AiBootstrapRequest
import com.vtempe.server.shared.dto.bootstrap.AiBootstrapResponse
import com.vtempe.server.shared.dto.chat.AiChatRequest
import com.vtempe.server.shared.dto.chat.AiChatResponse
import com.vtempe.server.shared.dto.nutrition.AiNutritionRequest
import com.vtempe.server.shared.dto.profile.AiProfile
import com.vtempe.server.shared.dto.training.AiTrainingRequest
import java.util.Locale
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory

class ChatService(
    private val llmClient: LLMClient,
    private val llmRepairer: LlmRepairer,
    private val aiService: AiService
) {
    private val logger = LoggerFactory.getLogger(ChatService::class.java)

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
    }

    suspend fun chat(req: AiChatRequest): AiChatResponse = runCatching {
        val locale = safeLocale(req.locale ?: req.profile.locale)
        val prompt = buildChatPrompt(req, locale)
        val requestId = "chat|${req.messages.size}|${req.messages.lastOrNull()?.content?.hashCode() ?: 0}"

        val response = withTimeout(LlmTimeoutMs) {
            llmRepairer.generate(
                logger = logger,
                operation = "chat",
                requestId = requestId,
                basePrompt = prompt,
                callModel = llmClient::generateJson,
                strategy = AiChatResponse.serializer(),
                validator = SchemaValidator { resp ->
                    validateChatResponse(resp)?.let(::listOf) ?: emptyList()
                },
                extractionMode = ExtractionMode.FirstJsonObject
            )
        }

        normalizeChatResponse(response, locale)
    }.getOrElse {
        logger.warn("LLM chat fallback triggered", it)
        AiChatResponse(
            reply = fallbackChatMessage(safeLocale(req.locale ?: req.profile.locale)),
            trainingPlan = null,
            nutritionPlan = null,
            sleepAdvice = null,
        )
    }

    suspend fun bootstrap(req: AiBootstrapRequest): AiBootstrapResponse = runCatching {
        aiService.bundle(req)
    }.getOrElse {
        logger.warn("Bootstrap bundle failed", it)
        AiBootstrapResponse(
            trainingPlan = aiService.training(AiTrainingRequest(req.profile, req.weekIndex)),
            nutritionPlan = aiService.nutrition(AiNutritionRequest(req.profile, req.weekIndex)),
            sleepAdvice = aiService.sleep(AiAdviceRequest(req.profile))
        )
    }

    private fun buildChatPrompt(req: AiChatRequest, locale: Locale): String {
        val localeTag = (req.locale ?: req.profile.locale)?.takeIf { it.isNotBlank() } ?: DefaultLocale
        val languageDisplay = locale.getDisplayLanguage(locale).ifBlank { locale.language.ifBlank { "English" } }
        val profileJson = json.encodeToString(AiProfile.serializer(), req.profile)
        val profileSummary = buildChatProfileSummary(req.profile)

        val lastUserMessage = req.messages.lastOrNull { it.role.equals("user", ignoreCase = true) }?.content
            ?: req.messages.lastOrNull()?.content
            ?: ""

        val history = req.messages.dropLast(1).joinToString("\n") { "${it.role}: ${it.content}" }

        return buildString {
            appendLine("You are a professional AI strength coach, nutritionist, and recovery expert guiding the same athlete long-term.")
            appendLine("User locale: $languageDisplay ($localeTag). Reply in that language and measurement system.")
            appendLine()
            appendLine("PROFILE CONTEXT (JSON):")
            appendLine(profileJson)
            appendLine()
            appendLine("KEY FACTS:")
            append(profileSummary)
            appendLine()
            appendLine("When replying: first acknowledge the latest user message, then provide clear next steps.")
            appendLine("Only update trainingPlan, nutritionPlan, or sleepAdvice when the user explicitly requests changes or new plans; otherwise return null for unchanged sections.")
            appendLine("Return STRICT JSON matching this schema (no comments or extra text):")
            appendLine("{\"reply\": String,")
            appendLine(" \"trainingPlan\": {\"weekIndex\": Int, \"workouts\": [{ \"id\": String, \"date\": String(YYYY-MM-DD), \"sets\": [{ \"exerciseId\": String, \"reps\": Int, \"weightKg\": Double?, \"rpe\": Double? }] }] } | null,")
            appendLine(" \"nutritionPlan\": {\"weekIndex\": Int, \"mealsByDay\": { DayLabel: [{ \"name\": String, \"ingredients\": [String], \"kcal\": Int, \"macros\": { \"proteinGrams\": Int, \"fatGrams\": Int, \"carbsGrams\": Int, \"kcal\": Int } }] }, \"shoppingList\": [String]} | null,")
            appendLine(" \"sleepAdvice\": {\"messages\": [String], \"disclaimer\": String?} | null }")
            appendLine()
            appendLine("Guidelines:")
            appendLine("- Set plan sections to null when no update is required; never return empty arrays to signal no change.")
            appendLine("- Nutrition updates MUST include integer macros fields in every meal object.")
            appendLine("- Ensure macros.kcal equals proteinGrams*4 + carbsGrams*4 + fatGrams*9 (+/- 20 kcal). Fix kcal rather than omitting fields.")
            appendLine("Do not include trailing commas or comments; output must be valid JSON.")
            appendLine("Latest user message: \"$lastUserMessage\".")
            appendLine("Conversation so far:")
            if (history.isNotBlank()) appendLine(history) else appendLine("No previous messages provided.")
        }
    }

    companion object {
        private val LlmTimeoutMs = Env["AI_CHAT_TIMEOUT_MS"]?.toLongOrNull()?.coerceAtLeast(15_000L) ?: 120_000L
        private const val DefaultLocale = "en-US"
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
        "Тренер временно недоступен. Пожалуйста, попробуйте ещё раз чуть позже."
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

