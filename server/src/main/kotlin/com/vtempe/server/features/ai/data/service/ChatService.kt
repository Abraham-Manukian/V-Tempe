package com.vtempe.server.features.ai.data.service

import com.vtempe.server.features.ai.data.llm.LLMClient
import com.vtempe.server.features.ai.data.llm.LlmRepairer
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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
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
    private val llmRouter = LlmClientRouter(paidLlmClient, freeLlmClient)
    private val editApplicator = CoachEditApplicator(exerciseCatalog, trainingPlanResolver)

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
                callModel = { currentPrompt -> llmRouter.generateWithFallback(logger, req.profile, currentPrompt, "chat", requestId) },
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

        val normalized = normalizeChatResponse(response, locale, req.profile, trainingPlanResolver)
        finalizeChatResponse(normalized, req, locale)
    }.getOrElse { error ->
        if (error is CancellationException && error !is TimeoutCancellationException) throw error
        if (!isExpectedLlmFailure(error)) {
            logger.error("Unexpected error in LLM chat — not falling back, surfacing as failure", error)
            throw error
        }
        logger.warn("LLM chat fallback triggered", error)
        AiQualityMetrics.recordFallback(logger, "chat", error)
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
    }.getOrElse { error ->
        if (error is CancellationException && error !is TimeoutCancellationException) throw error
        if (!isExpectedLlmFailure(error)) {
            logger.error("Unexpected error in bootstrap bundle — not falling back, surfacing as failure", error)
            throw error
        }
        logger.warn("Bootstrap bundle failed", error)
        AiBootstrapResponse(
            trainingPlan = aiService.training(AiTrainingRequest(req.profile, req.weekIndex, req.locale)),
            nutritionPlan = aiService.nutrition(AiNutritionRequest(req.profile, req.weekIndex, req.locale)),
            sleepAdvice = aiService.sleep(AiAdviceRequest(req.profile, req.locale))
        )
    }

    /**
     * Turns the AI's raw intent into the final plans the client will save. Two paths converge here:
     *  - editOps (surgical): applied to the plan the client sent, server-side, by CoachEditApplicator.
     *  - inline full plan: the EDIT-normalized trainingPlan/nutritionPlan the AI returned wholesale.
     *
     * A plan is only attached to the response when it ACTUALLY differs from what the user already
     * had — so the client's "plan updated" card (shown whenever a plan is non-null) never lies about
     * a change that didn't happen. When ops were rejected and nothing changed, a short localized note
     * is appended so the reply is honest too.
     */
    private fun finalizeChatResponse(
        normalized: AiChatResponse,
        req: AiChatRequest,
        locale: Locale
    ): AiChatResponse {
        val edit = editApplicator.apply(
            ops = normalized.editOps,
            currentTraining = req.currentTrainingPlan,
            currentNutrition = req.currentNutritionPlan,
            profile = req.profile,
        )

        val finalTraining = when {
            edit.trainingChanged -> edit.trainingPlan
            normalized.trainingPlan != null && normalized.trainingPlan != req.currentTrainingPlan ->
                normalized.trainingPlan
            else -> null
        }
        val finalNutrition = when {
            // editOps mutate meals directly but don't touch the derived shopping list — regenerate
            // it from the edited ingredients so the client shows a list that matches the new meals.
            edit.nutritionChanged -> edit.nutritionPlan?.let { regenerateShoppingList(it) }
            normalized.nutritionPlan != null && normalized.nutritionPlan != req.currentNutritionPlan ->
                normalized.nutritionPlan
            else -> null
        }

        val nothingChanged = finalTraining == null && finalNutrition == null &&
            normalized.sleepAdvice == null
        val reply = if (edit.rejections.isNotEmpty() && nothingChanged) {
            logger.info("Chat edit ops all rejected: {}", edit.rejections.joinToString(" | "))
            appendEditFailureNote(normalized.reply, locale)
        } else {
            normalized.reply
        }

        return normalized.copy(
            reply = reply,
            editOps = emptyList(), // internal protocol — never forwarded to the client
            trainingPlan = finalTraining,
            nutritionPlan = finalNutrition,
        )
    }

    private fun regenerateShoppingList(plan: com.vtempe.server.shared.dto.nutrition.AiNutritionResponse):
        com.vtempe.server.shared.dto.nutrition.AiNutritionResponse {
        val ingredients = plan.mealsByDay.values.flatten().flatMap { it.ingredients }
        val list = com.vtempe.server.features.ai.data.service.nutrition.ShoppingListNormalizer.normalize(ingredients)
        return plan.copy(shoppingList = list)
    }

    private fun appendEditFailureNote(reply: String, locale: Locale): String {
        val note = if (locale.language.equals("ru", ignoreCase = true)) {
            "Не получилось применить это изменение к плану — уточни, пожалуйста, что именно поменять."
        } else {
            "I couldn't apply that change to your plan — could you clarify exactly what to change?"
        }
        return if (reply.isBlank()) note else "$reply\n\n$note"
    }

    private fun buildChatPrompt(req: AiChatRequest, locale: Locale): String {
        val localeTag = (req.locale ?: req.profile.locale)?.takeIf { it.isNotBlank() } ?: DefaultLocale
        val languageDisplay = locale.getDisplayLanguage(locale).ifBlank { locale.language.ifBlank { "English" } }
        val profileJson = json.encodeToString(AiProfile.serializer(), req.profile)
        val profileSummary = buildChatProfileSummary(req.profile)
        val restrictionsSummary = nutritionRestrictionsPrompt(req.profile)
        val injuryPrompt = buildInjuryRestrictionsPrompt(req.profile.injuries)
        val trainingResolverPrompt = buildTrainingResolverPrompt(
            exerciseCatalog = exerciseCatalog,
            trainingPlanResolver = trainingPlanResolver,
            trainingModeRaw = req.profile.trainingMode,
            equipment = req.profile.equipment
        )
        // Concrete catalog ids the user can actually do — the AI MUST pick swap/add targets from
        // this exact list. Without it the model emits a localized display name ("тяга верхнего
        // блока") that can't be resolved to a catalog exercise, and the swap silently no-ops.
        val availableExerciseIds = run {
            val mode = trainingPlanResolver.resolveMode(req.profile.trainingMode, req.profile.equipment)
            val equip = trainingPlanResolver.normalizeEquipment(req.profile.equipment)
            exerciseCatalog.availablePatterns(mode, equip)
                .flatMap { exerciseCatalog.candidatesFor(it, mode, equip) }
                .map { it.id }
                .distinct()
                .sorted()
        }

        val lastUserMessage = req.messages.lastOrNull { it.role.equals("user", ignoreCase = true) }?.content
            ?: req.messages.lastOrNull()?.content
            ?: ""

        // Keep only the last 8 messages (4 turns) — older context is stale and can confuse
        // the AI when plans have changed since those turns were recorded.
        val history = req.messages.dropLast(1)
            .takeLast(8)
            .joinToString("\n") { msg ->
                val truncated = if (msg.content.length > 400) msg.content.take(400) + "…" else msg.content
                "${msg.role}: $truncated"
            }

        // Serialize current plans so the coach can make targeted edits
        val currentTrainingPlanJson = req.currentTrainingPlan?.let {
            runCatching { json.encodeToString(com.vtempe.server.shared.dto.training.AiTrainingResponse.serializer(), it) }.getOrNull()
        }
        val currentNutritionPlanJson = req.currentNutritionPlan?.let {
            runCatching { json.encodeToString(com.vtempe.server.shared.dto.nutrition.AiNutritionResponse.serializer(), it) }.getOrNull()
        }

        val todayIso = java.time.LocalDate.now().toString()
        val todayDow = java.time.LocalDate.now().dayOfWeek.name.lowercase().replaceFirstChar { it.uppercase() }

        return buildString {
            appendLine("You are a professional AI strength coach, nutritionist, and recovery expert guiding the same athlete long-term.")
            appendLine("User locale: $languageDisplay ($localeTag). Reply in that language and measurement system.")
            appendLine("TODAY'S DATE: $todayIso ($todayDow). Use this as the reference for 'today', 'tonight', 'this morning', 'tomorrow', etc.")
            appendLine("DAY TARGETING RULE: ONLY modify the meal or workout for the day the user explicitly refers to.")
            appendLine("  - 'today' → modify $todayIso only.")
            appendLine("  - 'tomorrow' → modify the next date only.")
            appendLine("  - 'Monday' or 'Понедельник' → find and modify the workout/meals on the Monday in the current plan.")
            appendLine("  - If the user says 'I don't want X today' — change ONLY today's meals, not any other day.")
            appendLine("  - NEVER change a different day from the one the user mentioned.")
            append(untrustedDataBlock("PROFILE CONTEXT (JSON)", profileJson))
            appendLine()
            appendLine("KEY FACTS:")
            append(profileSummary)
            if (injuryPrompt.isNotBlank()) {
                appendLine()
                append(injuryPrompt)
            }
            appendLine()
            appendLine("NUTRITION RESTRICTIONS (NON-NEGOTIABLE):")
            appendLine(restrictionsSummary)
            if (currentTrainingPlanJson != null) {
                appendLine()
                appendLine("CURRENT TRAINING PLAN (full week — use this when the user asks to modify specific workouts, exercises, sets, reps, or weights):")
                appendLine(currentTrainingPlanJson)
            }
            if (currentNutritionPlanJson != null) {
                appendLine()
                appendLine("CURRENT NUTRITION PLAN (full week — use this when the user asks to modify specific meals, days, or ingredients):")
                appendLine(currentNutritionPlanJson)
            }
            appendLine()
            appendLine("When replying: first acknowledge the latest user message, then provide clear next steps.")
            appendLine()
            appendLine("HOW TO APPLY A CHANGE — pick exactly ONE mechanism per request:")
            appendLine("1. editOps (PREFERRED for small, surgical changes): a list of precise edits applied to the")
            appendLine("   user's CURRENT plan on the server. Use for: changing one exercise, weight, reps, sets, one")
            appendLine("   ingredient, one meal's macros, renaming/removing/adding a single item. You do NOT copy the")
            appendLine("   whole plan — just emit the deltas. This is cheaper and cannot corrupt the untouched parts.")
            appendLine("2. Full plan (trainingPlan / nutritionPlan): only for LARGE changes where editOps would be")
            appendLine("   dozens of ops — e.g. regenerating a whole workout/day, translating the entire plan,")
            appendLine("   restructuring the week. Return the FULL updated plan with your changes applied.")
            appendLine("Never return BOTH editOps and a full plan for the same domain. If editOps cover it, keep the")
            appendLine("full plan null. Only touch the domain(s) the user actually asked about.")
            appendLine()
            appendLine("editOps operations (op + the fields it uses):")
            appendLine("  TRAINING. exerciseId identifies an EXISTING exercise in the current plan above (copy its")
            appendLine("  exact id). newExerciseId (for swap/add) MUST be one of these supported exercise ids —")
            appendLine("  copy an id VERBATIM, never invent one and never use a translated display name:")
            appendLine("    ${availableExerciseIds.joinToString(", ")}")
            appendLine("  If none of these fits what the user wants, say so in reply and emit no op — do not guess.")
            appendLine("   - swap_exercise: {op, workoutId?, exerciseId, newExerciseId}")
            appendLine("   - set_weight:    {op, workoutId?, exerciseId, weightKg}")
            appendLine("   - set_reps:      {op, workoutId?, exerciseId, reps}")
            appendLine("   - set_rpe:       {op, workoutId?, exerciseId, rpe}")
            appendLine("   - set_sets:      {op, workoutId?, exerciseId, sets}")
            appendLine("   - add_exercise:  {op, workoutId, newExerciseId, reps?, weightKg?, rpe?, sets?}")
            appendLine("   - remove_exercise:{op, workoutId?, exerciseId}")
            appendLine("   (workoutId omitted = apply to every workout containing that exercise.)")
            appendLine("  NUTRITION (day = Mon..Sun; target a meal by mealIndex OR mealName):")
            appendLine("   - set_ingredient:  {op, day, mealIndex|mealName, ingredientIndex, ingredient}")
            appendLine("   - add_ingredient:  {op, day, mealIndex|mealName, ingredient}")
            appendLine("   - remove_ingredient:{op, day, mealIndex|mealName, ingredientIndex|ingredient}")
            appendLine("   - set_meal_macros: {op, day, mealIndex|mealName, kcal?, proteinGrams?, fatGrams?, carbsGrams?}")
            appendLine("   - rename_meal:     {op, day, mealIndex|mealName, name}")
            appendLine("   - swap_meal:       {op, day, mealIndex|mealName, name, ingredients, kcal?, proteinGrams?, fatGrams?, carbsGrams?, recipe?}")
            appendLine("   - add_meal:        {op, day, name, ingredients, kcal?, proteinGrams?, fatGrams?, carbsGrams?, recipe?}")
            appendLine("   - remove_meal:     {op, day, mealIndex|mealName}")
            appendLine("  All ingredient text must keep a quantity + unit (\"150 г риса\"). Write ingredient/meal names in $languageDisplay.")
            appendLine()
            appendLine("Only update editOps, trainingPlan, nutritionPlan, or sleepAdvice when the user explicitly requests changes; otherwise leave them empty/null.")
            appendLine("CRITICAL: The \"reply\" field MUST always be a non-empty string. It is your conversational message to the user.")
            appendLine("NEVER claim you changed something unless you emitted the matching editOps or full plan. If you cannot express the change with the ops above, say so in reply and change nothing.")
            appendLine("Return STRICT JSON matching this schema (no comments or extra text):")
            appendLine("{\"reply\": String,  // REQUIRED — never empty, write your response here")
            appendLine(" \"editOps\": [{ \"op\": String, \"workoutId\": String?, \"exerciseId\": String?, \"newExerciseId\": String?, \"weightKg\": Double?, \"reps\": Int?, \"rpe\": Double?, \"sets\": Int?, \"day\": String?, \"mealIndex\": Int?, \"mealName\": String?, \"ingredientIndex\": Int?, \"ingredient\": String?, \"name\": String?, \"kcal\": Int?, \"proteinGrams\": Int?, \"fatGrams\": Int?, \"carbsGrams\": Int?, \"ingredients\": [String]?, \"recipe\": String? }],")
            appendLine(" \"actions\": [{\"type\": String, \"trainingMode\": String?, \"weekIndex\": Int?, \"notes\": String?}],")
            appendLine(" \"trainingPlan\": {\"weekIndex\": Int, \"workouts\": [{ \"id\": String, \"date\": String(YYYY-MM-DD), \"sets\": [{ \"exerciseId\": String, \"reps\": Int, \"weightKg\": Double?, \"rpe\": Double? }] }] } | null,")
            appendLine(" \"nutritionPlan\": {\"weekIndex\": Int, \"mealsByDay\": { DayLabel: [{ \"name\": String, \"ingredients\": [String], \"kcal\": Int, \"macros\": { \"proteinGrams\": Int, \"fatGrams\": Int, \"carbsGrams\": Int, \"kcal\": Int } }] }, \"shoppingList\": [String]} | null,")
            appendLine(" \"sleepAdvice\": {\"messages\": [String], \"disclaimer\": String?} | null }")
            appendLine()
            appendLine("Guidelines:")
            appendLine("- Set plan sections to null and editOps to [] when no update is required; never return empty arrays to signal no change.")
            appendLine("- actions are for whole-plan regeneration or view requests only (NOT edits — use editOps for edits).")
            appendLine("- Available action types: show_current_workout, rebuild_training_plan, switch_training_mode, rebuild_nutrition_plan, refresh_sleep_advice.")
            appendLine("- Use switch_training_mode with trainingMode set to one of: gym, home, outdoor, mixed.")
            appendLine("- Use show_current_workout when the user asks to display or explain the current workout without changing it.")
            appendLine("- Use rebuild_training_plan for regenerate/refresh/rebuild plan requests when no full handcrafted plan is needed.")
            appendLine("- Use rebuild_nutrition_plan for regenerate/refresh meal-plan requests when no full handcrafted nutrition plan is needed.")
            appendLine("- Use refresh_sleep_advice for recovery/sleep refresh requests when no handcrafted advice block is needed.")
            appendLine("- When updating trainingPlan, prefer resolver slot tokens in `exerciseId`; you may keep existing supported concrete ids if the user is editing a current exercise.")
            appendLine(trainingResolverPrompt)
            appendLine("- Nutrition full-plan updates MUST include integer macros fields in every meal object.")
            appendLine("- Nutrition updates must strictly obey allergy/intolerance restrictions above.")
            appendLine("- Ensure macros.kcal equals proteinGrams*4 + carbsGrams*4 + fatGrams*9 (+/- 20 kcal). Fix kcal rather than omitting fields.")
            appendLine("Do not include trailing commas or comments; output must be valid JSON.")
            val chatContent = buildString {
                appendLine("Latest user message: $lastUserMessage")
                appendLine("Conversation so far:")
                append(if (history.isNotBlank()) history else "No previous messages provided.")
            }
            append(untrustedDataBlock("USER CHAT MESSAGE + HISTORY", chatContent))
        }
    }

    // generateWithFallback(): shared with AiService, see LlmClientRouter.kt.

    companion object {
        private val LlmTimeoutMs = Env["AI_CHAT_TIMEOUT_MS"]?.toLongOrNull()?.coerceAtLeast(15_000L) ?: 120_000L
        private const val DefaultLocale = "en-US"
    }

    // shouldFallbackToFree(): shared with AiService, see LlmFallbackPolicy.kt.
}

private fun buildChatProfileSummary(profile: AiProfile): String = buildString {
    val weightFormatted = String.format(Locale.US, "%.1f", profile.weightKg)
    appendLine("- Demographics: ${profile.age} y/o ${profile.sex.lowercase(Locale.US)} | ${profile.heightCm} cm | $weightFormatted kg")
    appendLine("- Goal: ${profile.goal}")
    appendLine("- Experience level (1-5): ${profile.experienceLevel}")
    appendLine("- Training mode preference: ${profile.trainingMode}")
    appendLine("- Selected coach visual/persona id: ${profile.coachTrainerId}")
    val equipment = if (profile.equipment.isNotEmpty()) profile.equipment.joinToString(", ") { sanitizeInlineUserText(it) } else "bodyweight only"
    appendLine("- Available equipment (raw user text): $equipment")
    if (profile.injuries.isNotEmpty()) appendLine("- Injuries / limitations (raw user text): ${profile.injuries.joinToString(", ") { sanitizeInlineUserText(it) }}")
    if (profile.healthNotes.isNotEmpty()) appendLine("- Contraindications (raw user text): ${profile.healthNotes.joinToString(", ") { sanitizeInlineUserText(it) }}")
    if (profile.weeklySchedule.isNotEmpty()) {
        val available = profile.weeklySchedule.filterValues { it }.keys
        if (available.isNotEmpty()) appendLine("- Preferred training days: ${available.joinToString(", ") { sanitizeInlineUserText(it) }}")
    }
    if (profile.dietaryPreferences.isNotEmpty()) appendLine("- Dietary preferences (raw user text): ${profile.dietaryPreferences.joinToString(", ") { sanitizeInlineUserText(it) }}")
    if (profile.allergies.isNotEmpty()) appendLine("- Allergies (raw user text): ${profile.allergies.joinToString(", ") { sanitizeInlineUserText(it) }}")
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
    if (profile.sleepHistory.isNotEmpty()) {
        appendLine("- Sleep history (use to personalise recovery advice and adjust training intensity):")
        profile.sleepHistory.take(7).forEach { entry ->
            val h = entry.durationMinutes / 60
            val m = entry.durationMinutes % 60
            appendLine("  ${entry.date}: ${h}h ${m}min")
        }
        val avgMinutes = profile.sleepHistory.take(7).map { it.durationMinutes }.average()
        val avgH = avgMinutes.toInt() / 60
        val avgM = avgMinutes.toInt() % 60
        appendLine("  Average: ${avgH}h ${avgM}min/night")
    }
    if (profile.recentWeights.isNotEmpty()) {
        appendLine("- Body weight measurements (use to fine-tune calorie targets and assess progress):")
        profile.recentWeights.take(8).forEach { entry ->
            appendLine("  ${entry.date}: ${String.format(Locale.US, "%.1f", entry.weightKg)} kg")
        }
        if (profile.recentWeights.size >= 2) {
            val newest = profile.recentWeights.first().weightKg
            val oldest = profile.recentWeights.last().weightKg
            val diff = newest - oldest
            val trend = when {
                diff > 0.5 -> "gaining (+${String.format(Locale.US, "%.1f", diff)} kg)"
                diff < -0.5 -> "losing (${String.format(Locale.US, "%.1f", diff)} kg)"
                else -> "stable (${String.format(Locale.US, "%+.1f", diff)} kg)"
            }
            appendLine("  Weight trend: $trend")
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
): AiChatResponse {
    val normalizedReply = sanitizeText(response.reply).ifBlank {
        // LLM returned empty reply — use fallback so the user sees a real message
        fallbackChatMessage(locale)
    }
    return response.copy(
        reply = normalizedReply,
    actions = response.actions
        .mapNotNull { normalizeChatAction(it, profile) }
        .distinctBy { "${it.type}|${it.trainingMode.orEmpty()}|${it.weekIndex ?: -1}|${it.workoutId.orEmpty()}|${it.targetExerciseId.orEmpty()}|${it.replacementExerciseId.orEmpty()}" },
        trainingPlan = response.trainingPlan?.let {
            // EDIT mode: the chat path is always an edit of the existing plan, never a fresh
            // generation. GENERATE would rebuild the deterministic skeleton and silently overwrite
            // whatever exercise the user just asked the coach to change.
            normalizeTrainingPlan(it, profile, trainingPlanResolver, mode = NormalizationMode.EDIT)
        },
        nutritionPlan = response.nutritionPlan?.let { normalizeNutritionPlan(it, locale, profile) },
        sleepAdvice = response.sleepAdvice?.let(::normalizeAdvice)
    )
}

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

