package com.vtempe.server.features.ai.data.service

import com.vtempe.server.features.ai.data.llm.LLMClient
import com.vtempe.server.features.ai.data.llm.LlmRepairer
import com.vtempe.server.features.ai.data.llm.RateLimitException
import com.vtempe.server.features.ai.data.llm.decode.SchemaValidator
import com.vtempe.server.features.ai.data.llm.pipeline.ExtractionMode
import com.vtempe.server.features.ai.domain.model.TrainingMode
import com.vtempe.server.features.ai.domain.port.ExerciseCatalog
import com.vtempe.server.features.ai.domain.port.TrainingPlanResolver
import com.vtempe.server.config.Env
import com.vtempe.server.shared.dto.advice.AiAdviceRequest
import com.vtempe.server.shared.dto.bootstrap.AiBootstrapRequest
import com.vtempe.server.shared.dto.bootstrap.AiBootstrapResponse
import com.vtempe.server.shared.dto.chat.AiChatAction
import com.vtempe.server.shared.dto.chat.AiChatActionType
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
    private val paidLlmClient: LLMClient,
    private val freeLlmClient: LLMClient,
    private val llmRepairer: LlmRepairer,
    private val aiService: AiService,
    private val exerciseCatalog: ExerciseCatalog,
    private val trainingPlanResolver: TrainingPlanResolver
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
                callModel = { currentPrompt -> generateWithFallback(req.profile, currentPrompt, requestId) },
                strategy = AiChatResponse.serializer(),
                validator = SchemaValidator { resp ->
                    val normalized = normalizeChatResponse(resp, locale, req.profile, trainingPlanResolver)
                    val errors = validateChatResponse(normalized, req.profile, locale, exerciseCatalog)
                    val criticalErrors = AiQualityErrorPolicy.criticalErrors(errors)
                    if (criticalErrors.isNotEmpty()) {
                        AiQualityMetrics.recordValidation(logger, "chat", requestId, criticalErrors)
                    }
                    val warningErrors = AiQualityErrorPolicy.warningErrors(errors)
                    if (warningErrors.isNotEmpty()) {
                        logger.info(
                            "LLM chat requestId={} accepted with relaxed quality warnings={}",
                            requestId,
                            warningErrors.joinToString(" | ")
                        )
                    }
                    criticalErrors
                },
                extractionMode = ExtractionMode.FirstJsonObject
            )
        }

        normalizeChatResponse(response, locale, req.profile, trainingPlanResolver)
    }.getOrElse {
        logger.warn("LLM chat fallback triggered", it)
        AiQualityMetrics.recordFallback(logger, "chat", it)
        AiChatResponse(
            reply = fallbackChatMessage(safeLocale(req.locale ?: req.profile.locale)),
            actions = emptyList(),
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
            trainingPlan = aiService.training(AiTrainingRequest(req.profile, req.weekIndex, req.locale)),
            nutritionPlan = aiService.nutrition(AiNutritionRequest(req.profile, req.weekIndex, req.locale)),
            sleepAdvice = aiService.sleep(AiAdviceRequest(req.profile, req.locale))
        )
    }

    private fun buildChatPrompt(req: AiChatRequest, locale: Locale): String {
        val localeTag = (req.locale ?: req.profile.locale)?.takeIf { it.isNotBlank() } ?: DefaultLocale
        val languageDisplay = locale.getDisplayLanguage(locale).ifBlank { locale.language.ifBlank { "English" } }
        val profileJson = json.encodeToString(AiProfile.serializer(), req.profile)
        val profileSummary = buildChatProfileSummary(req.profile)
        val restrictionsSummary = nutritionRestrictionsPrompt(req.profile)
        val trainingResolverPrompt = buildTrainingResolverPrompt(
            exerciseCatalog = exerciseCatalog,
            trainingPlanResolver = trainingPlanResolver,
            trainingModeRaw = req.profile.trainingMode,
            equipment = req.profile.equipment
        )

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
            appendLine("NUTRITION RESTRICTIONS (NON-NEGOTIABLE):")
            appendLine(restrictionsSummary)
            appendLine()
            appendLine("When replying: first acknowledge the latest user message, then provide clear next steps.")
            appendLine("Only update trainingPlan, nutritionPlan, or sleepAdvice when the user explicitly requests changes or new plans; otherwise return null for unchanged sections.")
            appendLine("Return STRICT JSON matching this schema (no comments or extra text):")
            appendLine("{\"reply\": String,")
            appendLine(" \"actions\": [{\"type\": String, \"trainingMode\": String?, \"weekIndex\": Int?, \"notes\": String?, \"workoutId\": String?, \"targetExerciseId\": String?, \"replacementExerciseId\": String?}],")
            appendLine(" \"trainingPlan\": {\"weekIndex\": Int, \"workouts\": [{ \"id\": String, \"date\": String(YYYY-MM-DD), \"sets\": [{ \"exerciseId\": String, \"reps\": Int, \"weightKg\": Double?, \"rpe\": Double? }] }] } | null,")
            appendLine(" \"nutritionPlan\": {\"weekIndex\": Int, \"mealsByDay\": { DayLabel: [{ \"name\": String, \"ingredients\": [String], \"kcal\": Int, \"macros\": { \"proteinGrams\": Int, \"fatGrams\": Int, \"carbsGrams\": Int, \"kcal\": Int } }] }, \"shoppingList\": [String]} | null,")
            appendLine(" \"sleepAdvice\": {\"messages\": [String], \"disclaimer\": String?} | null }")
            appendLine()
            appendLine("Guidelines:")
            appendLine("- Set plan sections to null when no update is required; never return empty arrays to signal no change.")
            appendLine("- Prefer action objects over rewriting full plans when the app can safely execute the request itself.")
            appendLine("- Available action types: show_current_workout, replace_exercise, rebuild_training_plan, switch_training_mode, rebuild_nutrition_plan, refresh_sleep_advice.")
            appendLine("- Use switch_training_mode with trainingMode set to one of: gym, home, outdoor, mixed.")
            appendLine("- Use show_current_workout when the user asks to display or explain the current workout without changing it.")
            appendLine("- Use replace_exercise for local swaps inside the current plan instead of regenerating the entire week.")
            appendLine("- replace_exercise requires targetExerciseId. Provide replacementExerciseId when you know the exact supported exercise or pattern token. workoutId is optional but should be set when the user refers to one workout/day.")
            appendLine("- Use rebuild_training_plan for regenerate/refresh/rebuild plan requests when no full handcrafted plan is needed.")
            appendLine("- Use rebuild_nutrition_plan for regenerate/refresh meal-plan requests when no full handcrafted nutrition plan is needed.")
            appendLine("- Use refresh_sleep_advice for recovery/sleep refresh requests when no handcrafted advice block is needed.")
            appendLine("- If actions fully express the request, keep trainingPlan, nutritionPlan, and sleepAdvice null.")
            appendLine("- When updating trainingPlan, prefer resolver slot tokens in `exerciseId`; you may keep existing supported concrete ids if the user is editing a current exercise.")
            appendLine(trainingResolverPrompt)
            appendLine("- Nutrition updates MUST include integer macros fields in every meal object.")
            appendLine("- Nutrition updates must strictly obey allergy/intolerance restrictions above.")
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

    private suspend fun generateWithFallback(profile: AiProfile, prompt: String, requestId: String): String {
        val mode = profile.llmMode?.trim()?.lowercase()
        if (mode == "free") return freeLlmClient.generateJson(prompt)

        return runCatching {
            paidLlmClient.generateJson(prompt)
        }.recoverCatching { ex ->
            if (!shouldFallbackToFree(ex)) throw ex
            logger.warn(
                "LLM chat switching to free client requestId={} reason={}",
                requestId,
                ex.message ?: ex::class.simpleName
            )
            freeLlmClient.generateJson(prompt)
        }.getOrThrow()
    }

    private fun shouldFallbackToFree(error: Throwable): Boolean {
        if (error is RateLimitException) return true
        val message = error.message?.lowercase().orEmpty()
        if (message.contains(" 429") || message.contains("rate limit")) return true
        if (message.contains(" 402") || message.contains("insufficient credits") || message.contains("payment required")) return true
        if (message.contains(" 401") || message.contains("unauthorized")) return true
        if (message.contains(" 403") || message.contains("forbidden")) return true
        return message.contains("timed out") ||
            message.contains("timeout") ||
            message.contains("connection reset") ||
            message.contains("provider returned error")
    }
}

private fun buildChatProfileSummary(profile: AiProfile): String = buildString {
    val weightFormatted = String.format(Locale.US, "%.1f", profile.weightKg)
    appendLine("- Demographics: ${profile.age} y/o ${profile.sex.lowercase(Locale.US)} | ${profile.heightCm} cm | $weightFormatted kg")
    appendLine("- Goal: ${profile.goal}")
    appendLine("- Experience level (1-5): ${profile.experienceLevel}")
    appendLine("- Training mode preference: ${profile.trainingMode}")
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
    appendLine("- Nutrition budget level (1 low .. 3 high): ${profile.budgetLevel}")
    if (profile.recentWorkouts.isNotEmpty()) {
        appendLine("- Recent workout outcomes to adapt load, volume, and recovery:")
        profile.recentWorkouts.take(5).forEachIndexed { index, workout ->
            val completion = (workout.completionRate * 100.0).coerceIn(0.0, 100.0)
            val avgRpe = workout.averageRpe?.let { String.format(Locale.US, "%.1f", it) } ?: "n/a"
            val notes = workout.notes.takeIf { it.isNotBlank() } ?: "none"
            appendLine(
                "  ${index + 1}. ${workout.date}: ${workout.completedItems}/${workout.plannedItems} done, " +
                    "completion ${String.format(Locale.US, "%.0f", completion)}%, " +
                    "volume ${String.format(Locale.US, "%.1f", workout.totalVolumeKg)} kg, avg RPE $avgRpe, notes: $notes"
            )
        }
    }
}

private fun fallbackChatMessage(locale: Locale): String =
    if (locale.language.equals("ru", ignoreCase = true)) {
        "Тренер временно недоступен. Пожалуйста, попробуйте ещё раз чуть позже."
    } else {
        "Coach is temporarily unavailable. Please try again later."
    }

internal fun validateChatResponse(
    response: AiChatResponse,
    profile: AiProfile,
    locale: Locale,
    exerciseCatalog: ExerciseCatalog = builtInExerciseCatalog
): List<String> {
    val errors = mutableListOf<String>()
    if (response.reply.isBlank()) errors += "reply must contain user-facing text"
    errors += validateChatActions(response.actions)
    response.trainingPlan?.let { plan ->
        validateTrainingPlan(plan, exerciseCatalog)?.let { errors += "trainingPlan: $it" }
    }
    response.nutritionPlan?.let { plan ->
        errors += validateNutritionPlan(plan, profile, locale).map { "nutritionPlan: $it" }
    }
    response.sleepAdvice?.let { advice ->
        validateSleepAdvice(advice)?.let { errors += "sleepAdvice: $it" }
    }
    return errors.distinct()
}

private fun normalizeChatResponse(
    response: AiChatResponse,
    locale: Locale,
    profile: AiProfile,
    trainingPlanResolver: TrainingPlanResolver = builtInTrainingPlanResolver
): AiChatResponse = response.copy(
    reply = sanitizeText(response.reply),
    actions = response.actions
        .mapNotNull { normalizeChatAction(it, profile) }
        .distinctBy { "${it.type}|${it.trainingMode.orEmpty()}|${it.weekIndex ?: -1}|${it.workoutId.orEmpty()}|${it.targetExerciseId.orEmpty()}|${it.replacementExerciseId.orEmpty()}" },
    trainingPlan = response.trainingPlan?.let { normalizeTrainingPlan(it, profile, trainingPlanResolver) },
    nutritionPlan = response.nutritionPlan?.let { normalizeNutritionPlan(it, locale, profile) },
    sleepAdvice = response.sleepAdvice?.let(::normalizeAdvice)
)

private fun validateChatActions(actions: List<AiChatAction>): List<String> {
    if (actions.isEmpty()) return emptyList()

    val errors = mutableListOf<String>()
    actions.forEachIndexed { index, action ->
        val type = AiChatActionType.fromWire(action.type)
        if (type == null) {
            errors += "actions[$index].type '${action.type}' is not supported"
            return@forEachIndexed
        }

        if ((type == AiChatActionType.SWITCH_TRAINING_MODE || type == AiChatActionType.REBUILD_TRAINING_PLAN) &&
            action.trainingMode != null
        ) {
            val mode = TrainingMode.fromWire(action.trainingMode)
            if (mode == TrainingMode.AUTO) {
                errors += "actions[$index].trainingMode '${action.trainingMode}' is not supported"
            }
        }

        if (type == AiChatActionType.SWITCH_TRAINING_MODE && action.trainingMode.isNullOrBlank()) {
            errors += "actions[$index].trainingMode is required for switch_training_mode"
        }

        if (type == AiChatActionType.REPLACE_EXERCISE) {
            if (action.targetExerciseId.isNullOrBlank()) {
                errors += "actions[$index].targetExerciseId is required for replace_exercise"
            }
            if (action.replacementExerciseId.isNullOrBlank()) {
                errors += "actions[$index].replacementExerciseId is required for replace_exercise"
            }
            if (!action.targetExerciseId.isNullOrBlank() && action.targetExerciseId !in builtInExerciseCatalog.supportedExerciseIds()) {
                errors += "actions[$index].targetExerciseId '${action.targetExerciseId}' is not supported"
            }
            if (!action.replacementExerciseId.isNullOrBlank() && action.replacementExerciseId !in builtInExerciseCatalog.supportedExerciseIds()) {
                errors += "actions[$index].replacementExerciseId '${action.replacementExerciseId}' is not supported"
            }
            if (!action.targetExerciseId.isNullOrBlank() &&
                !action.replacementExerciseId.isNullOrBlank() &&
                action.targetExerciseId == action.replacementExerciseId
            ) {
                errors += "actions[$index] replacement must differ from target"
            }
        }

        if (action.weekIndex != null && action.weekIndex < 0) {
            errors += "actions[$index].weekIndex must be >= 0"
        }
    }

    return errors
}

private fun normalizeChatAction(action: AiChatAction, profile: AiProfile): AiChatAction? {
    val type = AiChatActionType.fromWire(action.type) ?: return null
    val normalizedMode = action.trainingMode
        ?.let { TrainingMode.fromWire(it) }
        ?.takeIf { it != TrainingMode.AUTO }
        ?.wireValue
    val normalizedTarget = action.targetExerciseId
        ?.let { builtInExerciseCatalog.findByIdOrAlias(it)?.id }
    val notes = action.notes?.let(::sanitizeText)?.takeIf { it.isNotBlank() }
    val normalizedWorkoutId = action.workoutId?.let(::sanitizeText)?.takeIf { it.isNotBlank() }
    val normalizedReplacement = normalizeReplacementExerciseId(
        action = action,
        normalizedMode = normalizedMode,
        normalizedTarget = normalizedTarget,
        profile = profile
    )

    return AiChatAction(
        type = type.wireValue,
        trainingMode = normalizedMode,
        weekIndex = action.weekIndex?.coerceAtLeast(0),
        notes = notes,
        workoutId = normalizedWorkoutId,
        targetExerciseId = normalizedTarget,
        replacementExerciseId = normalizedReplacement
    )
}

private fun normalizeReplacementExerciseId(
    action: AiChatAction,
    normalizedMode: String?,
    normalizedTarget: String?,
    profile: AiProfile
): String? {
    val rawReplacement = action.replacementExerciseId?.trim()?.takeIf { it.isNotEmpty() }
    val replacementByAlias = rawReplacement?.let { builtInExerciseCatalog.findByIdOrAlias(it)?.id }
    if (replacementByAlias != null) return replacementByAlias

    if (rawReplacement != null) {
        val resolved = builtInTrainingPlanResolver.resolveExerciseId(
            rawToken = rawReplacement,
            trainingModeRaw = normalizedMode ?: profile.trainingMode,
            equipment = profile.equipment,
            usedExerciseIds = normalizedTarget?.let(::setOf).orEmpty(),
            rotationSeed = rawReplacement.hashCode()
        )
        if (resolved != null) return resolved
    }

    val target = normalizedTarget?.let { builtInExerciseCatalog.findByIdOrAlias(it) } ?: return null
    val candidates = builtInExerciseCatalog.candidatesFor(
        pattern = target.primaryPattern,
        mode = TrainingMode.fromWire(normalizedMode ?: profile.trainingMode),
        equipment = builtInTrainingPlanResolver.normalizeEquipment(profile.equipment)
    )

    return candidates
        .firstOrNull { it.id != target.id }
        ?.id
}

