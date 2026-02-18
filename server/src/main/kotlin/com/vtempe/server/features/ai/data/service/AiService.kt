package com.vtempe.server.features.ai.data.service

import com.vtempe.server.shared.dto.advice.AiAdviceRequest
import com.vtempe.server.shared.dto.advice.AiAdviceResponse
import com.vtempe.server.shared.dto.bootstrap.AiBootstrapRequest
import com.vtempe.server.shared.dto.bootstrap.AiBootstrapResponse
import com.vtempe.server.shared.dto.nutrition.AiMeal
import com.vtempe.server.shared.dto.nutrition.AiNutritionRequest
import com.vtempe.server.shared.dto.nutrition.AiNutritionResponse
import com.vtempe.server.shared.dto.nutrition.Macros
import com.vtempe.server.shared.dto.profile.AiProfile
import com.vtempe.server.shared.dto.training.AiSet
import com.vtempe.server.shared.dto.training.AiTrainingRequest
import com.vtempe.server.shared.dto.training.AiTrainingResponse
import com.vtempe.server.shared.dto.training.AiWorkout
import com.vtempe.server.features.ai.data.llm.LLMClient
import com.vtempe.server.features.ai.data.llm.LlmRepairer
import com.vtempe.server.features.ai.data.llm.decode.SchemaValidator
import com.vtempe.server.features.ai.data.llm.pipeline.ExtractionMode
import java.nio.ByteBuffer
import java.nio.charset.Charset
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.temporal.TemporalAdjusters
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import kotlin.collections.buildList
import kotlin.collections.linkedMapOf
import kotlin.math.abs
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory

class AiService(
    private val llmClient: LLMClient,
    private val llmRepairer: LlmRepairer
) {

    private val logger = LoggerFactory.getLogger(AiService::class.java)

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
    }

    suspend fun training(req: AiTrainingRequest): AiTrainingResponse = runWithFallback(
        operation = "training",
        fallback = { fallbackTraining(req) }
    ) {
        fetchBundle(req.profile, req.weekIndex, req.profile.locale).trainingPlan
            ?: throw IllegalStateException("trainingPlan missing in bundle")
    }

    suspend fun nutrition(req: AiNutritionRequest): AiNutritionResponse = runWithFallback(
        operation = "nutrition",
        fallback = { fallbackNutrition(req) }
    ) {
        fetchBundle(req.profile, req.weekIndex, req.profile.locale).nutritionPlan
            ?: throw IllegalStateException("nutritionPlan missing in bundle")
    }

    suspend fun sleep(req: AiAdviceRequest): AiAdviceResponse = runWithFallback(
        operation = "sleep",
        fallback = { fallbackAdvice(req) }
    ) {
        fetchBundle(req.profile, 0, req.profile.locale).sleepAdvice
            ?: throw IllegalStateException("sleepAdvice missing in bundle")
    }

    suspend fun bundle(req: AiBootstrapRequest): AiBootstrapResponse = runWithFallback(
        operation = "bundle",
        fallback = {
            AiBootstrapResponse(
                trainingPlan = fallbackTraining(
                    AiTrainingRequest(
                        req.profile,
                        req.weekIndex
                    )
                ),
                nutritionPlan = fallbackNutrition(
                    AiNutritionRequest(
                        req.profile,
                        req.weekIndex
                    )
                ),
                sleepAdvice = fallbackAdvice(AiAdviceRequest(req.profile))
            )
        }
    ) {
        fetchBundle(req.profile, req.weekIndex, req.locale)
    }

    private suspend fun <T> runWithFallback(
        operation: String,
        fallback: () -> T,
        block: suspend () -> T
    ): T = runCatching {
        withTimeout(LlmTimeoutMs) { block() }
    }.getOrElse {
        logger.warn("LLM operation fallback triggered for {}", operation, it)
        fallback()
    }

    private suspend fun fetchBundle(
        profile: AiProfile,
        weekIndex: Int,
        localeRaw: String?
    ): AiBootstrapResponse {
        val localeTag = localeRaw?.takeIf { it.isNotBlank() } ?: DEFAULT_LOCALE
        val profileHash = json.encodeToString(AiProfile.serializer(), profile).hashCode()
        val cacheKey = cacheKey(profile, weekIndex, localeTag)
        logger.debug(
            "LLM {} requestId={} locale={} weekIndex={} profileHash={}",
            "coach-bundle",
            cacheKey,
            localeTag,
            weekIndex,
            profileHash
        )
        val now = System.currentTimeMillis()

        cacheMutex.withLock {
            bundleCache[cacheKey]?.let { entry ->
                if (now - entry.timestamp <= BundleCacheTtlMs) {
                    return entry.bundle
                } else {
                    bundleCache.remove(cacheKey)
                }
            }
        }

        var deferred: CompletableDeferred<AiBootstrapResponse>? = null
        var isOwner = false
        cacheMutex.withLock {
            bundleCache[cacheKey]?.let { entry ->
                if (System.currentTimeMillis() - entry.timestamp <= BundleCacheTtlMs) {
                    return entry.bundle
                } else {
                    bundleCache.remove(cacheKey)
                }
            }
            val existing = inFlightBundles[cacheKey]
            if (existing != null) {
                deferred = existing
            } else {
                val created = CompletableDeferred<AiBootstrapResponse>()
                inFlightBundles[cacheKey] = created
                deferred = created
                isOwner = true
            }
        }

        val pending = deferred ?: error("Deferred bootstrap result missing")
        if (!isOwner) {
            return pending.await()
        }

        val locale = safeLocale(localeTag)
        val measurementSystem = measurementSystemLabel(locale)
        val weightUnit = if (measurementSystem.startsWith("imperial")) "pounds" else "kilograms"
        val request = AiBootstrapRequest(profile, weekIndex, localeTag)
        val prompt = buildBundlePrompt(locale, measurementSystem, weightUnit, request)

        return try {
            val generated = llmRepairer.generate(
                logger = logger,
                operation = "coach-bundle",
                requestId = cacheKey,
                basePrompt = prompt,
                callModel = llmClient::generateJson,
                strategy = AiBootstrapResponse.serializer(),
                validator = SchemaValidator { bundle ->
                    validateBundle(bundle)?.let(::listOf) ?: emptyList()
                },
                extractionMode = ExtractionMode.FirstJsonObject
            )
            val normalized = normalizeBundle(generated, locale)
            cacheMutex.withLock {
                bundleCache[cacheKey] = CacheEntry(normalized, System.currentTimeMillis())
                inFlightBundles.remove(cacheKey)?.complete(normalized)
            }
            normalized
        } catch (t: Throwable) {
            cacheMutex.withLock {
                inFlightBundles.remove(cacheKey)?.completeExceptionally(t)
            }
            throw t
        }
    }

    private fun buildBundlePrompt(
        locale: Locale,
        measurementSystem: String,
        weightUnit: String,
        request: AiBootstrapRequest
    ): String {
        val languageDisplay = locale.getDisplayLanguage(locale).ifBlank { locale.language.ifBlank { "English" } }
        val profileJson = json.encodeToString(AiProfile.serializer(), request.profile)
        val preferencesSummary = buildPreferencesSummary(request.profile)
        return buildString {
            appendLine("You are an elite strength coach, nutritionist, and recovery specialist guiding this athlete long-term.")
            appendLine("User language: $languageDisplay. Reply strictly in this language and measurement system.")
            appendLine("Return ONLY a single JSON object and nothing else (no markdown, introductions, or explanations).")
            appendLine("Do not escape quotes (no \\\" sequences). Output must be valid JSON.")
            appendLine()
            appendLine("PROFILE CONTEXT (JSON):")
            appendLine(profileJson)
            appendLine()
            appendLine("KEY FACTS ABOUT THE ATHLETE:")
            append(preferencesSummary)
            appendLine()
            appendLine("RESPONSE SCHEMA (STRICT JSON):")
            appendLine("{\"trainingPlan\": {\"weekIndex\": Int, \"workouts\": [{\"id\": String, \"date\": \"YYYY-MM-DD\", \"sets\": [{\"exerciseId\": String, \"reps\": Int, \"weightKg\": Double?, \"rpe\": Double?}]}]},")
            appendLine(" \"nutritionPlan\": {\"weekIndex\": Int, \"mealsByDay\": {DayLabel: [{\"name\": String, \"ingredients\": [String], \"kcal\": Int, \"macros\": {\"proteinGrams\": Int, \"fatGrams\": Int, \"carbsGrams\": Int, \"kcal\": Int}}]}, \"shoppingList\": [String]},")
            appendLine(" \"sleepAdvice\": {\"messages\": [String], \"disclaimer\": String}}")
            appendLine()
            appendLine("VALID RESPONSE EXAMPLE (structure only, replace with real data):")
            appendLine("{\"trainingPlan\":{\"weekIndex\":0,\"workouts\":[{\"id\":\"w_0_0\",\"date\":\"2025-01-06\",\"sets\":[{\"exerciseId\":\"squat\",\"reps\":8,\"weightKg\":60.0,\"rpe\":7.5}]}]},")
            appendLine("\"nutritionPlan\":{\"weekIndex\":0,\"mealsByDay\":{\"Mon\":[{\"name\":\"Oats with berries\",\"ingredients\":[\"rolled oats\",\"milk\",\"berries\"],\"kcal\":420,\"macros\":{\"proteinGrams\":35,\"fatGrams\":12,\"carbsGrams\":55,\"kcal\":420}}]},\"shoppingList\":[\"rolled oats\",\"milk\",\"berries\"]},")
            appendLine("\"sleepAdvice\":{\"messages\":[\"Keep a consistent sleep schedule.\"],\"disclaimer\":\"Not medical advice\"}}")
            appendLine("")
            appendLine("")
            appendLine("{\"messages\":[\"\\u041b\\u043e\\u0436\\u0438\\u0442\\u0435\\u0441\u044c \\u0438 \\u043f\\u0440\u043e\u0441\u044b\u043f\u0430\\u0439\u0442\u0435\u0441\u044c \\u0432 \\u043e\u0434\u043d\u043e \\u0438 \\u0442\u043e \\u0436\\u0435 \\u0432\u0440\u0435\u043c\u044f.\"],\"disclaimer\":\"\\u0421\\u043e\\u0432\u0435\u0442\u044b \\u043d\\u0435 \\u0437\\u0430\u043c\u0435\u043d\u044f\\u044e\u0442 \\u0432\u0440\u0430\u0447\u0430. \\u041f\u0440\u0438 \\u043f\u0440\u043e\u0431\u043b\u0435\u043c\u0430\u0445 \\u043e\u0431\u0440\u0430\u0442\u0438\u0442\u0435\u0441\u044c \\u043a \\u0441\u043f\u0435\u0446\u0438\u0430\u043b\u0438\u0441\u0442\u0443.\"}")
            appendLine("All arrays must close properly. No missing commas, no trailing commas, no duplicated braces.")
            appendLine()
            appendLine("TRAINING RULES:")
            appendLine("- Use ISO dates (YYYY-MM-DD). Plan for week index ${request.weekIndex} (upcoming week).")
            appendLine("- Measurement system: $measurementSystem. Weights must be in $weightUnit (use null for bodyweight).")
            appendLine("- Provide balanced push/pull/legs/core coverage. 4-6 exercises per workout, vary rep ranges 4-12, include RPE 6.5-9.0.")
            appendLine("- exerciseId must be chosen from: [squat, bench, deadlift, ohp, row, pullup, lunge, dip, pushup, curl, tricep_extension, plank, hip_thrust, leg_press].")
            appendLine("- Limit workouts to at most 5 in the plan and sets to at most 6 per workout to keep the JSON concise.")
            appendLine()
            appendLine("NUTRITION RULES:")
            appendLine("- Cover every day Mon..Sun. Pick meal frequency dynamically within goal ranges: lose weight -> 3-4 meals/day, maintain -> 3-5 meals/day, gain -> 4-6 meals/day. Do not hardcode two meals; choose what best fits the athlete.")
            appendLine("- Derive daily calories via Mifflin-St Jeor BMR (men: 10*kg + 6.25*cm - 5*age + 5; women: 10*kg + 6.25*cm - 5*age - 161) multiplied by activity factor (use 1.2 if 0 training days, 1.375 for 1-2 days, 1.55 for 3-4 days, 1.725 for 5+ days; if schedule missing assume 3 training days). Adjust for goal: lose = TDEE - 15%, maintain = TDEE, gain = TDEE + 10%. Never drop below calculated BMR.")
            appendLine("- Set daily macros before splitting into meals: protein 1.6-2.2 g/kg bodyweight, fat >= 0.8 g/kg, carbs fill the remaining calories. Distribute totals across meals proportionally and keep integers.")
            appendLine("- The sum of meals for any day must stay within +/-5% of the goal-adjusted daily calories and macros you calculated.")
            appendLine("- Ingredients must be plain text strings (no numbering or markdown). Keep meal names varied, localized, and practical.")
            appendLine("- Each meal MUST include integer macros {proteinGrams, fatGrams, carbsGrams, kcal}. No field may be null or omitted.")
            appendLine("- Example meal object: {\"name\":\"Power Oats\",\"ingredients\":[\"rolled oats\",\"milk\",\"berries\"],\"kcal\":420,\"macros\":{\"proteinGrams\":35,\"fatGrams\":12,\"carbsGrams\":55,\"kcal\":420}}")
            appendLine("- Ensure macros.kcal equals proteinGrams*4 + carbsGrams*4 + fatGrams*9 (allowed difference +/-20 kcal). If it does not match, adjust kcal to satisfy the formula.")
            appendLine("- Do not include a shoppingList field; it will be computed downstream from the ingredients you provide.")
            appendLine("- Close each day array with ] before defining the next day. No nested day structures.")
            appendLine()
            appendLine("SLEEP ADVICE RULES:")
            appendLine("- Provide 5-7 concise tips referencing training stress and recovery routines.")
            appendLine("- Disclaimer must clearly state the tips are informational and not medical advice.")
            appendLine()
            appendLine("GLOBAL VALIDATION RULES:")
            appendLine("- Training workout dates must be ISO (YYYY-MM-DD) and use future week index ${request.weekIndex}.")
            appendLine("- Day keys in mealsByDay must be exactly Mon,Tue,Wed,Thu,Fri,Sat,Sun in that order.")
            appendLine("- exerciseId values must stay within [squat, bench, deadlift, ohp, row, pullup, lunge, dip, pushup, curl, tricep_extension, plank, hip_thrust, leg_press].")
            appendLine("- For every meal, kcal must follow 4*protein + 4*carbs + 9*fat within +/-20 as already stated.")
            appendLine("- Daily meal frequencies you output must respect the goal ranges listed above; reject plans that fall outside the permitted meal counts.")
            appendLine("- All dates in nutrition or training contexts must be valid calendar dates (ISO format).")
            appendLine("Ensure that every array (for example mealsByDay[Day]) closes with ] before the enclosing object } closes.")
            appendLine("Never output double }} or any sequence like }}} right before ]. Always end JSON with a single }.")
        }
    }
    private fun buildPreferencesSummary(profile: AiProfile): String = buildString {
        val weightFormatted = String.format(Locale.US, "%.1f", profile.weightKg)
        appendLine("- Demographics: ${profile.age} y/o ${profile.sex.lowercase(Locale.US)} | ${profile.heightCm} cm | $weightFormatted kg")
        appendLine("- Goal: ${profile.goal}")
        appendLine("- Experience level (1-5): ${profile.experienceLevel}")

        val equipment = if (profile.equipment.isNotEmpty()) {
            profile.equipment.joinToString(", ")
        } else {
            "bodyweight only"
        }
        appendLine("- Available equipment: $equipment")

        if (profile.injuries.isNotEmpty()) {
            appendLine("- Injuries / limitations: ${profile.injuries.joinToString(", ")}")
        }
        if (profile.healthNotes.isNotEmpty()) {
            appendLine("- Contraindications / medical notes: ${profile.healthNotes.joinToString(", ")}")
        }
        if (profile.weeklySchedule.isNotEmpty()) {
            val available = profile.weeklySchedule.filterValues { it }.keys
            val unavailable = profile.weeklySchedule.filterValues { !it }.keys
            if (available.isNotEmpty()) {
                appendLine("- Preferred training days: ${available.joinToString(", ")}")
            }
            if (unavailable.isNotEmpty()) {
                appendLine("- Rest / unavailable days: ${unavailable.joinToString(", ")}")
            }
        }
        if (profile.dietaryPreferences.isNotEmpty()) {
            appendLine("- Dietary preferences: ${profile.dietaryPreferences.joinToString(", ")}")
        }
        if (profile.allergies.isNotEmpty()) {
            appendLine("- Allergies to avoid: ${profile.allergies.joinToString(", ")}")
        }
        appendLine("- Nutrition budget level (1 low .. 3 high): ${profile.budgetLevel ?: 2}")
    }

    private fun extractTextSignals(response: AiBootstrapResponse): List<String> = buildList {
        response.trainingPlan?.let { plan ->
            addAll(plan.workouts.flatMap { workout ->
                buildList {
                    add(workout.id)
                    add(workout.date)
                    addAll(workout.sets.map { set -> "${set.exerciseId}:${set.reps}" })
                }
            })
        }
        response.nutritionPlan?.let { plan ->
            addAll(plan.mealsByDay.values.flatten().flatMap { meal ->
                buildList {
                    add(meal.name)
                    addAll(meal.ingredients)
                }
            })
            addAll(plan.shoppingList)
        }
        response.sleepAdvice?.let { advice ->
            addAll(advice.messages)
            advice.disclaimer?.let { add(it) }
        }
    }

    private fun decodeBundleResponse(raw: String): AiBootstrapResponse {
        val sections = extractBundleSections(raw)
        val training = json.decodeFromString(AiTrainingResponse.serializer(), sections.training)
        val nutrition = json.decodeFromString(AiNutritionResponse.serializer(), sections.nutrition)
        val sleep = json.decodeFromString(AiAdviceResponse.serializer(), sections.sleep)
        return AiBootstrapResponse(
            trainingPlan = training,
            nutritionPlan = nutrition,
            sleepAdvice = sleep
        )
    }

    private data class BundleSections(val training: String, val nutrition: String, val sleep: String)

    private fun extractBundleSections(raw: String): BundleSections {
        val normalized = raw.replace("\r", "").trim()
        val training = findSectionJson(normalized, "TRAINING_JSON")
        val nutrition = findSectionJson(normalized, "NUTRITION_JSON")
        val sleep = findSectionJson(normalized, "SLEEP_JSON")
        return BundleSections(training, nutrition, sleep)
    }

    private fun findSectionJson(source: String, marker: String): String {
        val index = source.indexOf(marker)
        if (index < 0) {
            throw IllegalStateException("Expected marker $marker in LLM response")
        }
        val jsonStart = source.indexOf('{', index + marker.length)
        if (jsonStart < 0) {
            throw IllegalStateException("Expected JSON object after $marker")
        }
        val (jsonBlock, _) = extractJsonObject(source, jsonStart)
        return jsonBlock.trim()
    }

    private fun extractJsonObject(source: String, startIndex: Int): Pair<String, Int> {
        require(startIndex in source.indices && source[startIndex] == '{') {
            "extractJsonObject must start at an opening brace"
        }
        var depth = 0
        var inString = false
        var escape = false
        var index = startIndex
        while (index < source.length) {
            val ch = source[index]
            if (escape) {
                escape = false
            } else {
                when (ch) {
                    '\\' -> escape = true
                    '"' -> inString = !inString
                    '{' -> if (!inString) depth++
                    '}' -> if (!inString) {
                        depth--
                        if (depth == 0) {
                            return source.substring(startIndex, index + 1) to (index + 1)
                        }
                    }
                }
            }
            index++
        }
        throw IllegalStateException("Unclosed JSON object starting at index $startIndex")
    }

    private fun validateBundle(bundle: AiBootstrapResponse): String? {
        val training = bundle.trainingPlan ?: return "trainingPlan was null"
        validateTrainingPlan(training)?.let { return "trainingPlan: $it" }
        val nutrition = bundle.nutritionPlan ?: return "nutritionPlan was null"
        validateNutritionPlan(nutrition)?.let { return "nutritionPlan: $it" }
        val advice = bundle.sleepAdvice ?: return "sleepAdvice was null"
        validateSleepAdvice(advice)?.let { return "sleepAdvice: $it" }
        return null
    }

    private fun measurementSystemLabel(locale: Locale): String =
        if (usesImperial(locale)) "imperial (pounds/inches)" else "metric (kilograms/centimeters)"

    private fun withFeedback(basePrompt: String, attempt: Int, feedback: String?): String = buildString {
        append(basePrompt)
        if (feedback != null) {
            appendLine()
            appendLine("Previous attempt issue (#${attempt - 1}): $feedback")
            appendLine("Return the response again using the TRAINING_JSON / NUTRITION_JSON / SLEEP_JSON blocks exactly as specified.")
        }
    }

    private fun cacheKey(profile: AiProfile, weekIndex: Int, localeTag: String): String {
        val fingerprint = json.encodeToString(AiProfile.serializer(), profile)
        return "${fingerprint.hashCode()}|$weekIndex|$localeTag"
    }

    private fun fallbackTraining(req: AiTrainingRequest): AiTrainingResponse {
        val locale = safeLocale(req.profile.locale)
        val today = LocalDate.now(ZoneOffset.UTC)
        fun set(id: String, reps: Int, weight: Double?, rpe: Double?) =
            AiSet(localizedExerciseLabel(id, locale), reps, weight, rpe)

        val workouts = List(3) { day ->
            val id = "w_${req.weekIndex}_${day + 1}"
            val date = today.plusDays(day.toLong()).toString()
            val sets = when (day) {
                0 -> listOf(
                    set("squat", 6, 60.0, 7.0),
                    set("bench", 8, 45.0, 7.0),
                    set("row", 10, 40.0, 7.0)
                )
                1 -> listOf(
                    set("deadlift", 4, 90.0, 7.5),
                    set("ohp", 8, 30.0, 7.0),
                    set("pullup", 6, null, 7.0)
                )
                else -> listOf(
                    set("lunge", 10, 25.0, 7.0),
                    set("hip_thrust", 12, 50.0, 7.0),
                    set("plank", 45, null, 6.5)
                )
            }
            AiWorkout(id = id, date = date, sets = sets)
        }
        return AiTrainingResponse(req.weekIndex, workouts)
    }

    private fun fallbackNutrition(req: AiNutritionRequest): AiNutritionResponse {
        val locale = safeLocale(req.profile.locale)
        val meals = templateMeals(locale)
        val days = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
        val planMeals = days.associateWith { day -> if (day == "Sun") meals.take(3) else meals }
        val shopping = meals.flatMap { it.ingredients }
            .map(::sanitizeText)
            .filter { it.isNotEmpty() }
            .distinct()
            .sorted()
        return AiNutritionResponse(req.weekIndex, planMeals, shopping)
    }

    private fun fallbackAdvice(req: AiAdviceRequest): AiAdviceResponse {
        val locale = safeLocale(req.profile.locale)
        val isRu = locale.language.equals("ru", ignoreCase = true)
        val messages = if (isRu) {
            listOf(
                "\u041b\u043e\u0436\u0438\u0442\u0435\u0441\u044c \u0438 \u043f\u0440\u043e\u0441\u044b\u043f\u0430\u0439\u0442\u0435\u0441\u044c \u0432 \u043e\u0434\u043d\u043e \u0438 \u0442\u043e \u0436\u0435 \u0432\u0440\u0435\u043c\u044f, \u0434\u0430\u0436\u0435 \u043f\u043e \u0432\u044b\u0445\u043e\u0434\u043d\u044b\u043c.",
                "\u0417\u0430 \u0447\u0430\u0441 \u0434\u043e \u0441\u043d\u0430 \u043f\u0440\u0438\u0433\u043b\u0443\u0448\u0438\u0442\u0435 \u0441\u0432\u0435\u0442 \u0438 \u0443\u0431\u0435\u0440\u0438\u0442\u0435 \u044f\u0440\u043a\u0438\u0435 \u044d\u043a\u0440\u0430\u043d\u044b.",
                "\u041f\u043e\u0434\u0434\u0435\u0440\u0436\u0438\u0432\u0430\u0439\u0442\u0435 \u043f\u0440\u043e\u0445\u043b\u0430\u0434\u0443 \u0438 \u0442\u0438\u0448\u0438\u043d\u0443 \u0432 \u0441\u043f\u0430\u043b\u044c\u043d\u0435 (18\u201320 \u00b0C).",
                "\u0418\u0437\u0431\u0435\u0433\u0430\u0439\u0442\u0435 \u0442\u044f\u0436\u0451\u043b\u043e\u0439 \u0435\u0434\u044b \u0438 \u043a\u043e\u0444\u0435\u0438\u043d\u0430 \u0437\u0430 3 \u0447\u0430\u0441\u0430 \u0434\u043e \u0441\u043d\u0430.",
                "\u0414\u043e\u0431\u0430\u0432\u044c\u0442\u0435 \u043b\u0451\u0433\u043a\u0443\u044e \u0440\u0430\u0441\u0442\u044f\u0436\u043a\u0443 \u0438\u043b\u0438 \u0434\u044b\u0445\u0430\u0442\u0435\u043b\u044c\u043d\u044b\u0435 \u0443\u043f\u0440\u0430\u0436\u043d\u0435\u043d\u0438\u044f \u043f\u0435\u0440\u0435\u0434 \u0441\u043d\u043e\u043c.",
                "\u0415\u0441\u043b\u0438 \u043d\u043e\u0447\u044c \u043f\u0440\u043e\u0448\u043b\u0430 \u043f\u043b\u043e\u0445\u043e, \u0441\u043d\u0438\u0437\u044c\u0442\u0435 \u043d\u0430\u0433\u0440\u0443\u0437\u043a\u0443 \u043d\u0430 \u0441\u043b\u0435\u0434\u0443\u044e\u0449\u0435\u0439 \u0442\u0440\u0435\u043d\u0438\u0440\u043e\u0432\u043a\u0435."
            )
        } else {
            listOf(
                "Stick to consistent bed and wake times, including weekends.",
                "Dim screens and bright lights at least an hour before bed.",
                "Keep the bedroom cool, dark, and ventilated (18-20C).",
                "Avoid heavy meals and caffeine within three hours of bedtime.",
                "Schedule light stretching or breathing exercises to wind down.",
                "Reduce training load the day after poor sleep."
            )
        }
        val disclaimer = if (isRu) {
            "\u0421\u043e\u0432\u0435\u0442\u044b \u043d\u043e\u0441\u044f\u0442 \u043e\u0437\u043d\u0430\u043a\u043e\u043c\u0438\u0442\u0435\u043b\u044c\u043d\u044b\u0439 \u0445\u0430\u0440\u0430\u043a\u0442\u0435\u0440. \u041f\u0440\u0438 \u0441\u0435\u0440\u044c\u0451\u0437\u043d\u044b\u0445 \u043d\u0430\u0440\u0443\u0448\u0435\u043d\u0438\u044f\u0445 \u0441\u043d\u0430 \u043e\u0431\u0440\u0430\u0442\u0438\u0442\u0435\u0441\u044c \u043a \u0432\u0440\u0430\u0447\u0443."
        } else {
            "Coaching tips only. Consult a medical professional if sleep issues persist."
        }
        return AiAdviceResponse(messages, disclaimer)
    }

    private fun localizedExerciseLabel(id: String, locale: Locale): String {
        val labels = fallbackExerciseLabels[id]
        val isRu = locale.language.equals("ru", ignoreCase = true)
        val base = when {
            labels != null && isRu -> labels.second
            labels != null -> labels.first
            else -> sanitizeText(id.replace('_', ' '))
        }
        return if (labels != null && isRu) "$base ($id)" else base
    }

    companion object {
        private const val LlmTimeoutMs = 120_000L
        private const val DEFAULT_LOCALE = "en-US"
        private const val BundleCacheTtlMs = 30 * 60 * 1000L

        private val logger = LoggerFactory.getLogger(AiService::class.java)
        private val bundleCache = ConcurrentHashMap<String, CacheEntry>()
        private val cacheMutex = Mutex()
        private val inFlightBundles = ConcurrentHashMap<String, CompletableDeferred<AiBootstrapResponse>>()
        private val fallbackExerciseLabels = mapOf(
            "squat" to ("Back Squat" to "\u041f\u0440\u0438\u0441\u0435\u0434\u0430\u043d\u0438\u044f \u0441\u043e \u0448\u0442\u0430\u043d\u0433\u043e\u0439"),
            "bench" to ("Bench Press" to "\u0416\u0438\u043c \u043b\u0451\u0436\u0430"),
            "deadlift" to ("Deadlift" to "\u0421\u0442\u0430\u043d\u043e\u0432\u0430\u044f \u0442\u044f\u0433\u0430"),
            "ohp" to ("Overhead Press" to "\u0416\u0438\u043c \u0441\u0442\u043e\u044f"),
            "row" to ("Bent-Over Row" to "\u0422\u044f\u0433\u0430 \u0432 \u043d\u0430\u043a\u043b\u043e\u043d\u0435"),
            "pullup" to ("Pull-up" to "\u041f\u043e\u0434\u0442\u044f\u0433\u0438\u0432\u0430\u043d\u0438\u044f"),
            "lunge" to ("Walking Lunge" to "\u0412\u044b\u043f\u0430\u0434\u044b"),
            "hip_thrust" to ("Hip Thrust" to "\u042f\u0433\u043e\u0434\u0438\u0447\u043d\u044b\u0439 \u043c\u043e\u0441\u0442"),
            "plank" to ("Plank Hold" to "\u041f\u043b\u0430\u043d\u043a\u0430"),
            "dip" to ("Parallel Bar Dip" to "\u041e\u0442\u0436\u0438\u043c\u0430\u043d\u0438\u044f \u043d\u0430 \u0431\u0440\u0443\u0441\u044c\u044f\u0445"),
            "pushup" to ("Push-up" to "\u041e\u0442\u0436\u0438\u043c\u0430\u043d\u0438\u044f"),
            "curl" to ("Biceps Curl" to "\u0421\u0433\u0438\u0431\u0430\u043d\u0438\u044f \u043d\u0430 \u0431\u0438\u0446\u0435\u043f\u0441"),
            "tricep_extension" to ("Triceps Extension" to "\u0420\u0430\u0437\u0433\u0438\u0431\u0430\u043d\u0438\u044f \u043d\u0430 \u0442\u0440\u0438\u0446\u0435\u043f\u0441"),
            "leg_press" to ("Leg Press" to "\u0416\u0438\u043c \u043d\u043e\u0433\u0430\u043c\u0438")
        )
    }

    private data class CacheEntry(val bundle: AiBootstrapResponse, val timestamp: Long)
}

internal const val MacroCalorieTolerance = 40

private val cp1251Charset: Charset = Charset.forName("windows-1251")
private val cyrillicRange = '\u0400'..'\u04FF'
private val whitespaceRegex = Regex("\\s+")

private fun sanitizeText(raw: String): String {
    val trimmed = raw.trim()
    val decoded = decodeCp1251(trimmed) ?: trimmed
    return whitespaceRegex.replace(decoded, " ").trim()
}
private fun decodeCp1251(raw: String): String? {
    if (!looksLikeCp1251Garbage(raw)) return null
    val bytes = raw.map { (it.code and 0xFF).toByte() }.toByteArray()
    return runCatching {
        val decoded = cp1251Charset.decode(ByteBuffer.wrap(bytes)).toString()
        val cyrillicCount = decoded.count { it in cyrillicRange }
        if (cyrillicCount >= decoded.length / 3) decoded else null
    }.getOrNull()
}

private fun looksLikeCp1251Garbage(raw: String): Boolean =
    raw.any { ch ->
        ch.code in 0x2500..0x257F ||
            ch.code in 0x2580..0x259F ||
            ch.code in 0x00C0..0x00FF
    }

internal fun normalizeTrainingPlan(plan: AiTrainingResponse): AiTrainingResponse {
    val weekStart = expectedWeekStart(plan.weekIndex)
    return plan.copy(
        workouts = plan.workouts.mapIndexed { index, workout ->
            val safeDate = normalizeWorkoutDate(workout.date, weekStart, index)
            workout.copy(
                date = safeDate,
                id = sanitizeText(workout.id).ifEmpty { workout.id },
                sets = workout.sets.map { set ->
                    val trimmedId = sanitizeText(set.exerciseId).ifEmpty { set.exerciseId }
                    val weight = set.weightKg?.takeIf { it >= 0.0 }
                    val rpe = set.rpe?.takeIf { it > 0.0 }
                    set.copy(
                        exerciseId = trimmedId,
                        weightKg = weight,
                        rpe = rpe
                    )
                }
            )
        }
    )
}

internal fun normalizeNutritionPlan(plan: AiNutritionResponse, locale: Locale): AiNutritionResponse {
    val fallbackMeals = templateMeals(locale)
    val normalizedMealsByDay = linkedMapOf<String, List<AiMeal>>()

    plan.mealsByDay.forEach { (dayRaw, meals) ->
        val dayKey = sanitizeText(dayRaw).ifEmpty { dayRaw.trim() }.ifEmpty { "Day" }
        val cleanedMeals = meals.mapNotNull { meal ->
            val name = sanitizeText(meal.name)
            val ingredients = meal.ingredients.map(::sanitizeText).filter { it.isNotEmpty() }
            if (name.isEmpty() || ingredients.isEmpty()) {
                null
            } else {
                val normalizedMacros = normalizeMacros(meal.macros)
                val normalizedKcal = if (meal.kcal <= 0 || abs(meal.kcal - normalizedMacros.kcal) > MacroCalorieTolerance) {
                    normalizedMacros.kcal
                } else {
                    meal.kcal
                }
                AiMeal(
                    name = name,
                    ingredients = ingredients,
                    kcal = normalizedKcal,
                    macros = normalizedMacros
                )
            }
        }
        val safeMeals = if (cleanedMeals.isEmpty()) fallbackMeals else cleanedMeals
        normalizedMealsByDay[dayKey] = safeMeals
    }

    val normalizedShopping = (plan.shoppingList + normalizedMealsByDay.values.flatten().flatMap { it.ingredients })
        .map(::sanitizeText)
        .filter { it.isNotEmpty() }
        .distinct()
        .sorted()

    return plan.copy(
        mealsByDay = normalizedMealsByDay,
        shoppingList = normalizedShopping
    )
}

internal fun normalizeAdvice(advice: AiAdviceResponse): AiAdviceResponse {
    val normalizedMessages = advice.messages.map(::sanitizeText).filter { it.isNotEmpty() }
    val normalizedDisclaimer = advice.disclaimer?.let(::sanitizeText)?.takeIf { it.isNotEmpty() }
    return advice.copy(
        messages = if (normalizedMessages.isEmpty()) advice.messages else normalizedMessages,
        disclaimer = normalizedDisclaimer ?: advice.disclaimer
    )
}

internal fun normalizeBundle(bundle: AiBootstrapResponse, locale: Locale): AiBootstrapResponse =
    AiBootstrapResponse(
        trainingPlan = bundle.trainingPlan?.let { normalizeTrainingPlan(it) },
        nutritionPlan = bundle.nutritionPlan?.let { normalizeNutritionPlan(it, locale) },
        sleepAdvice = bundle.sleepAdvice?.let { normalizeAdvice(it) }
    )

internal fun normalizeBundle(bundle: AiBootstrapResponse): AiBootstrapResponse =
    normalizeBundle(bundle, Locale.ENGLISH)

internal fun templateMeals(locale: Locale): List<AiMeal> {
    val isRu = locale.language.equals("ru", ignoreCase = true)
    return if (isRu) {
        listOf(
            AiMeal(
                name = "\u041e\u0432\u0441\u044f\u043d\u043a\u0430 \u0441 \u044f\u0433\u043e\u0434\u0430\u043c\u0438",
                ingredients = listOf(
                    "\u043e\u0432\u0441\u044f\u043d\u044b\u0435 \u0445\u043b\u043e\u043f\u044c\u044f",
                    "\u043c\u043e\u043b\u043e\u043a\u043e",
                    "\u044f\u0433\u043e\u0434\u044b",
                    "\u043c\u0451\u0434"
                ),
                kcal = 420,
                macros = Macros(35, 12, 55, 420)
            ),
            AiMeal(
                name = "\u041a\u0443\u0440\u0438\u043d\u0430\u044f \u0433\u0440\u0443\u0434\u043a\u0430 \u0441 \u0440\u0438\u0441\u043e\u043c \u0438 \u043e\u0432\u043e\u0449\u0430\u043c\u0438",
                ingredients = listOf(
                    "\u043a\u0443\u0440\u0438\u043d\u0430\u044f \u0433\u0440\u0443\u0434\u043a\u0430",
                    "\u0440\u0438\u0441",
                    "\u0431\u0440\u043e\u043a\u043a\u043e\u043b\u0438",
                    "\u043c\u043e\u0440\u043a\u043e\u0432\u044c"
                ),
                kcal = 520,
                macros = Macros(45, 15, 50, 520)
            ),
            AiMeal(
                name = "\u0413\u0440\u0435\u0447\u0435\u0441\u043a\u0438\u0439 \u0439\u043e\u0433\u0443\u0440\u0442 \u0441 \u043e\u0440\u0435\u0445\u0430\u043c\u0438",
                ingredients = listOf(
                    "\u0433\u0440\u0435\u0447\u0435\u0441\u043a\u0438\u0439 \u0439\u043e\u0433\u0443\u0440\u0442",
                    "\u0433\u0440\u0435\u0446\u043a\u0438\u0435 \u043e\u0440\u0435\u0445\u0438",
                    "\u043c\u0451\u0434"
                ),
                kcal = 420,
                macros = Macros(25, 18, 35, 420)
            ),
            AiMeal(
                name = "\u041b\u043e\u0441\u043e\u0441\u044c \u0441 \u043a\u0438\u043d\u043e\u0430 \u0438 \u0448\u043f\u0438\u043d\u0430\u0442\u043e\u043c",
                ingredients = listOf(
                    "\u0444\u0438\u043b\u0435 \u043b\u043e\u0441\u043e\u0441\u044f",
                    "\u043a\u0438\u043d\u043e\u0430",
                    "\u0448\u043f\u0438\u043d\u0430\u0442",
                    "\u043b\u0438\u043c\u043e\u043d"
                ),
                kcal = 560,
                macros = Macros(40, 20, 45, 560)
            )
        )
    } else {
        listOf(
            AiMeal(
                name = "Oatmeal with Berries",
                ingredients = listOf("rolled oats", "milk", "blueberries", "honey"),
                kcal = 420,
                macros = Macros(35, 12, 55, 420)
            ),
            AiMeal(
                name = "Chicken Bowl with Veggies",
                ingredients = listOf("chicken breast", "rice", "broccoli", "carrots"),
                kcal = 520,
                macros = Macros(45, 15, 50, 520)
            ),
            AiMeal(
                name = "Greek Yogurt Parfait",
                ingredients = listOf("greek yogurt", "walnuts", "honey"),
                kcal = 420,
                macros = Macros(25, 18, 35, 420)
            ),
            AiMeal(
                name = "Salmon with Quinoa and Spinach",
                ingredients = listOf("salmon fillet", "quinoa", "spinach", "lemon"),
                kcal = 560,
                macros = Macros(40, 20, 45, 560)
            )
        )
    }
}

private fun expectedWeekStart(weekIndex: Int): LocalDate {
    val today = LocalDate.now(ZoneOffset.UTC)
    val nextMonday = today.with(TemporalAdjusters.nextOrSame(DayOfWeek.MONDAY))
    return nextMonday.plusWeeks(weekIndex.toLong())
}

private fun normalizeWorkoutDate(raw: String, weekStart: LocalDate, index: Int): String {
    val parsed = runCatching { LocalDate.parse(raw) }.getOrElse { weekStart.plusDays(index.toLong()) }
    val weekEnd = weekStart.plusDays(6)
    val safeDate = if (parsed.isBefore(weekStart) || parsed.isAfter(weekEnd)) {
        weekStart.plusDays(index.toLong().coerceAtMost(6))
    } else {
        parsed
    }
    return safeDate.toString()
}

internal fun validateTrainingPlan(plan: AiTrainingResponse): String? {
    if (plan.workouts.isEmpty()) return "workouts array must contain at least one workout"
    if (plan.workouts.any { it.sets.isEmpty() }) return "each workout must include at least one set"
    return null
}

internal fun validateNutritionPlan(plan: AiNutritionResponse): String? {
    if (plan.mealsByDay.isEmpty()) return "mealsByDay must contain entries for the week"
    val validMeals = plan.mealsByDay.values
        .flatten()
        .count { meal ->
            meal.name.isNotBlank() && meal.ingredients.any { it.isNotBlank() }
        }
    if (validMeals == 0) {
        return "mealsByDay must include at least one meal with a name and ingredients."
    }
    return null
}

internal fun validateSleepAdvice(advice: AiAdviceResponse): String? {
    if (advice.messages.isEmpty()) return "messages must contain at least one tip"
    return null
}

internal fun normalizeMacros(macros: Macros): Macros {
    val protein = macros.proteinGrams.coerceAtLeast(0)
    val fat = macros.fatGrams.coerceAtLeast(0)
    val carbs = macros.carbsGrams.coerceAtLeast(0)
    val computedKcal = computeKcal(protein, carbs, fat)
    val kcal = when {
        macros.kcal <= 0 -> computedKcal
        abs(macros.kcal - computedKcal) > MacroCalorieTolerance -> computedKcal
        else -> macros.kcal
    }
    return Macros(proteinGrams = protein, fatGrams = fat, carbsGrams = carbs, kcal = kcal)
}

internal fun computeKcal(proteinGrams: Int, carbsGrams: Int, fatGrams: Int): Int =
    proteinGrams.coerceAtLeast(0) * 4 +
        carbsGrams.coerceAtLeast(0) * 4 +
        fatGrams.coerceAtLeast(0) * 9

internal fun safeLocale(tag: String?): Locale {
    val candidate = tag?.let { runCatching { Locale.forLanguageTag(it) }.getOrNull() }
    return if (candidate == null || candidate.language.isNullOrBlank()) Locale.ENGLISH else candidate
}

private fun usesImperial(locale: Locale): Boolean =
    locale.country.equals("US", ignoreCase = true) ||
        locale.country.equals("LR", ignoreCase = true) ||
        locale.country.equals("MM", ignoreCase = true)
