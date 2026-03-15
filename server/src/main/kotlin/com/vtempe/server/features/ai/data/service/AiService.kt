package com.vtempe.server.features.ai.data.service

import com.vtempe.server.config.Env
import com.vtempe.server.features.ai.data.llm.LLMClient
import com.vtempe.server.features.ai.data.llm.LlmRepairer
import com.vtempe.server.features.ai.data.llm.RateLimitException
import com.vtempe.server.features.ai.data.llm.decode.SchemaValidator
import com.vtempe.server.features.ai.data.llm.pipeline.ExtractionMode
import com.vtempe.server.shared.dto.advice.AiAdviceRequest
import com.vtempe.server.shared.dto.advice.AiAdviceResponse
import com.vtempe.server.shared.dto.bootstrap.AiBootstrapRequest
import com.vtempe.server.shared.dto.bootstrap.AiBootstrapResponse
import com.vtempe.server.shared.dto.nutrition.AiNutritionResponse
import com.vtempe.server.shared.dto.profile.AiProfile
import com.vtempe.server.shared.dto.training.AiTrainingResponse
import com.vtempe.server.shared.dto.training.AiTrainingRequest
import com.vtempe.server.shared.dto.nutrition.AiNutritionRequest
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory

class AiService(
    private val paidLlmClient: LLMClient,
    private val freeLlmClient: LLMClient,
    private val llmRepairer: LlmRepairer
) {

    private val logger = LoggerFactory.getLogger(AiService::class.java)

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
    }

    suspend fun training(req: AiTrainingRequest) = runWithFallback(
        operation = "training",
        fallback = { fallbackTraining(req) }
    ) {
        fetchBundle(req.profile, req.weekIndex, req.locale ?: req.profile.locale).trainingPlan
            ?: throw IllegalStateException("trainingPlan missing in bundle")
    }

    suspend fun nutrition(req: AiNutritionRequest) = runWithFallback(
        operation = "nutrition",
        fallback = { fallbackNutrition(req) }
    ) {
        fetchBundle(req.profile, req.weekIndex, req.locale ?: req.profile.locale).nutritionPlan
            ?: throw IllegalStateException("nutritionPlan missing in bundle")
    }

    suspend fun sleep(req: AiAdviceRequest) = runWithFallback(
        operation = "sleep",
        fallback = { fallbackAdvice(req) }
    ) {
        fetchBundle(req.profile, 0, req.locale ?: req.profile.locale).sleepAdvice
            ?: throw IllegalStateException("sleepAdvice missing in bundle")
    }

    suspend fun bundle(req: AiBootstrapRequest): AiBootstrapResponse = runWithFallback(
        operation = "bundle",
        fallback = {
            AiBootstrapResponse(
                trainingPlan = fallbackTraining(AiTrainingRequest(req.profile, req.weekIndex, req.locale)),
                nutritionPlan = fallbackNutrition(AiNutritionRequest(req.profile, req.weekIndex, req.locale)),
                sleepAdvice = fallbackAdvice(AiAdviceRequest(req.profile, req.locale))
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
        AiQualityMetrics.recordFallback(logger, operation, it)
        fallback()
    }

    private suspend fun fetchBundle(
        profile: AiProfile,
        weekIndex: Int,
        localeRaw: String?
    ): AiBootstrapResponse {
        val localeTag = localeRaw?.takeIf { it.isNotBlank() } ?: DefaultLocale
        val profileHash = json.encodeToString(AiProfile.serializer(), profile).hashCode()
        val requestId = cacheKey(profile, weekIndex, localeTag)
        logger.debug(
            "LLM {} requestId={} locale={} weekIndex={} profileHash={}",
            "coach-bundle",
            requestId,
            localeTag,
            weekIndex,
            profileHash
        )

        val cached = loadCachedBundle(requestId)
        if (cached != null) return cached

        val pendingState = lockInFlightBundle(requestId)
        if (!pendingState.isOwner) {
            return pendingState.deferred.await()
        }

        val locale = safeLocale(localeTag)
        val measurementSystem = measurementSystemLabel(locale)
        val weightUnit = if (measurementSystem.startsWith("imperial")) "pounds" else "kilograms"
        var decomposedAttempted = false

        if (PreferDecomposedBundleFirst) {
            decomposedAttempted = true
            val decomposed = attemptDecomposedBundle(
                profile = profile,
                weekIndex = weekIndex,
                localeTag = localeTag,
                locale = locale,
                requestId = requestId
            )
            if (decomposed != null) {
                cacheMutex.withLock {
                    bundleCache[requestId] = CacheEntry(
                        bundle = decomposed,
                        timestamp = System.currentTimeMillis(),
                        ttlMs = BundleCacheTtlMs
                    )
                    inFlightBundles.remove(requestId)?.complete(decomposed)
                }
                return decomposed
            }
            logger.warn("Decomposed-first bundle generation failed, falling back to monolithic requestId={}", requestId)
        }

        val prompt = buildBundlePrompt(
            json = json,
            locale = locale,
            measurementSystem = measurementSystem,
            weightUnit = weightUnit,
            request = AiBootstrapRequest(profile, weekIndex, localeTag)
        )

        return try {
            val generated = llmRepairer.generate(
                logger = logger,
                operation = "coach-bundle",
                requestId = requestId,
                basePrompt = prompt,
                callModel = { currentPrompt -> generateWithFallback(profile, currentPrompt, "coach-bundle", requestId) },
                strategy = AiBootstrapResponse.serializer(),
                validator = SchemaValidator { bundle ->
                    val normalizedCandidate = normalizeBundle(bundle, locale, profile)
                    val errors = validateBundle(normalizedCandidate, profile, locale)
                    val criticalErrors = AiQualityErrorPolicy.criticalErrors(errors)
                    if (criticalErrors.isNotEmpty()) {
                        AiQualityMetrics.recordValidation(logger, "coach-bundle", requestId, criticalErrors)
                    }
                    val warningErrors = AiQualityErrorPolicy.warningErrors(errors)
                    if (warningErrors.isNotEmpty()) {
                        logger.info(
                            "LLM coach-bundle requestId={} accepted with relaxed quality warnings={}",
                            requestId,
                            warningErrors.joinToString(" | ")
                        )
                    }
                    criticalErrors
                },
                extractionMode = ExtractionMode.FirstJsonObject
            )

            val normalized = normalizeBundle(generated, locale, profile)
            cacheMutex.withLock {
                bundleCache[requestId] = CacheEntry(
                    bundle = normalized,
                    timestamp = System.currentTimeMillis(),
                    ttlMs = BundleCacheTtlMs
                )
                inFlightBundles.remove(requestId)?.complete(normalized)
            }
            normalized
        } catch (t: Throwable) {
            val decomposed = if (!decomposedAttempted && shouldAttemptDecomposedGeneration(t)) {
                attemptDecomposedBundle(
                    profile = profile,
                    weekIndex = weekIndex,
                    localeTag = localeTag,
                    locale = locale,
                    requestId = requestId
                )
            } else {
                null
            }
            if (decomposed != null) {
                cacheMutex.withLock {
                    bundleCache[requestId] = CacheEntry(
                        bundle = decomposed,
                        timestamp = System.currentTimeMillis(),
                        ttlMs = BundleCacheTtlMs
                    )
                    inFlightBundles.remove(requestId)?.complete(decomposed)
                }
                return decomposed
            }

            val fallbackBundle = normalizeBundle(
                AiBootstrapResponse(
                    trainingPlan = fallbackTraining(AiTrainingRequest(profile, weekIndex, localeTag)),
                    nutritionPlan = fallbackNutrition(AiNutritionRequest(profile, weekIndex, localeTag)),
                    sleepAdvice = fallbackAdvice(AiAdviceRequest(profile, localeTag))
                ),
                locale,
                profile
            )
            logger.warn("LLM bundle fallback activated for requestId={}", requestId, t)
            AiQualityMetrics.recordFallback(logger, "coach-bundle", t)
            cacheMutex.withLock {
                bundleCache[requestId] = CacheEntry(
                    bundle = fallbackBundle,
                    timestamp = System.currentTimeMillis(),
                    ttlMs = FallbackCacheTtlMs
                )
                inFlightBundles.remove(requestId)?.complete(fallbackBundle)
            }
            fallbackBundle
        }
    }

    private suspend fun attemptDecomposedBundle(
        profile: AiProfile,
        weekIndex: Int,
        localeTag: String,
        locale: java.util.Locale,
        requestId: String
    ): AiBootstrapResponse? {
        if (!EnableDecomposedBundle) return null

        return runCatching {
            coroutineScope {
                AiQualityMetrics.recordDecomposedBundle(logger, requestId)
                val measurementSystem = measurementSystemLabel(locale)
                val weightUnit = if (measurementSystem.startsWith("imperial")) "pounds" else "kilograms"

                val trainingDeferred = async {
                    runCatching {
                        generateTrainingSection(
                            profile = profile,
                            weekIndex = weekIndex,
                            localeTag = localeTag,
                            locale = locale,
                            measurementSystem = measurementSystem,
                            weightUnit = weightUnit,
                            requestId = requestId
                        )
                    }.onFailure {
                        logger.warn("Decomposed training generation failed requestId={}", requestId, it)
                    }.getOrElse {
                        fallbackTraining(AiTrainingRequest(profile, weekIndex, localeTag))
                    }
                }

                val nutritionDeferred = async {
                    runCatching {
                        generateNutritionSection(
                            profile = profile,
                            weekIndex = weekIndex,
                            localeTag = localeTag,
                            locale = locale,
                            measurementSystem = measurementSystem,
                            weightUnit = weightUnit,
                            requestId = requestId
                        )
                    }.onFailure {
                        logger.warn("Decomposed nutrition generation failed requestId={}", requestId, it)
                    }.getOrElse {
                        fallbackNutrition(AiNutritionRequest(profile, weekIndex, localeTag))
                    }
                }

                val adviceDeferred = async {
                    runCatching {
                        generateAdviceSection(
                            profile = profile,
                            localeTag = localeTag,
                            locale = locale,
                            requestId = requestId
                        )
                    }.onFailure {
                        logger.warn("Decomposed sleep advice generation failed requestId={}", requestId, it)
                    }.getOrElse {
                        fallbackAdvice(AiAdviceRequest(profile, localeTag))
                    }
                }

                val training = trainingDeferred.await()
                val nutrition = nutritionDeferred.await()
                val advice = adviceDeferred.await()

                val bundle = normalizeBundle(
                    AiBootstrapResponse(
                        trainingPlan = training,
                        nutritionPlan = nutrition,
                        sleepAdvice = advice
                    ),
                    locale,
                    profile
                )
                val errors = validateBundle(bundle, profile, locale)
                val criticalErrors = AiQualityErrorPolicy.criticalErrors(errors)
                if (criticalErrors.isNotEmpty()) {
                    AiQualityMetrics.recordValidation(logger, "coach-bundle-decomposed", requestId, criticalErrors)
                    throw IllegalStateException("Decomposed bundle failed validation: ${criticalErrors.joinToString(" | ")}")
                }
                val warningErrors = AiQualityErrorPolicy.warningErrors(errors)
                if (warningErrors.isNotEmpty()) {
                    logger.warn(
                        "Decomposed bundle accepted with non-critical quality warnings requestId={} warnings={}",
                        requestId,
                        warningErrors.joinToString(" | ")
                    )
                }
                bundle
            }
        }.onFailure {
            logger.warn("Decomposed bundle generation failed requestId={}", requestId, it)
        }.getOrNull()
    }

    private suspend fun generateTrainingSection(
        profile: AiProfile,
        weekIndex: Int,
        localeTag: String,
        locale: java.util.Locale,
        measurementSystem: String,
        weightUnit: String,
        requestId: String
    ): AiTrainingResponse {
        val prompt = buildTrainingSectionPrompt(
            profile = profile,
            weekIndex = weekIndex,
            locale = locale,
            localeTag = localeTag,
            measurementSystem = measurementSystem,
            weightUnit = weightUnit
        )
        val sectionRequestId = "$requestId|training"
        val generated = llmRepairer.generate(
            logger = logger,
            operation = "training-section",
            requestId = sectionRequestId,
            basePrompt = prompt,
            callModel = { currentPrompt -> generateWithFallback(profile, currentPrompt, "training-section", sectionRequestId) },
            strategy = AiTrainingResponse.serializer(),
            validator = SchemaValidator { plan ->
                val normalized = normalizeTrainingPlan(plan)
                validateTrainingPlan(normalized)?.let(::listOf) ?: emptyList()
            },
            extractionMode = ExtractionMode.FirstJsonObject
        )
        return normalizeTrainingPlan(generated)
    }

    private suspend fun generateNutritionSection(
        profile: AiProfile,
        weekIndex: Int,
        localeTag: String,
        locale: java.util.Locale,
        measurementSystem: String,
        weightUnit: String,
        requestId: String
    ): AiNutritionResponse {
        val prompt = buildNutritionSectionPrompt(
            profile = profile,
            weekIndex = weekIndex,
            locale = locale,
            localeTag = localeTag,
            measurementSystem = measurementSystem,
            weightUnit = weightUnit
        )
        val sectionRequestId = "$requestId|nutrition"
        val generated = llmRepairer.generate(
            logger = logger,
            operation = "nutrition-section",
            requestId = sectionRequestId,
            basePrompt = prompt,
                callModel = { currentPrompt -> generateWithFallback(profile, currentPrompt, "nutrition-section", sectionRequestId) },
                strategy = AiNutritionResponse.serializer(),
                validator = SchemaValidator { plan ->
                    val normalized = normalizeNutritionPlan(plan, locale, profile)
                    val errors = validateNutritionPlan(normalized, profile, locale)
                    val criticalErrors = AiQualityErrorPolicy.criticalErrors(errors)
                    if (criticalErrors.isNotEmpty()) {
                        AiQualityMetrics.recordValidation(logger, "nutrition-section", sectionRequestId, criticalErrors)
                    }
                    val warningErrors = AiQualityErrorPolicy.warningErrors(errors)
                    if (warningErrors.isNotEmpty()) {
                        logger.info(
                            "LLM nutrition-section requestId={} accepted with relaxed quality warnings={}",
                            sectionRequestId,
                            warningErrors.joinToString(" | ")
                        )
                    }
                    criticalErrors
                },
                extractionMode = ExtractionMode.FirstJsonObject
            )
        return normalizeNutritionPlan(generated, locale, profile)
    }

    private suspend fun generateAdviceSection(
        profile: AiProfile,
        localeTag: String,
        locale: java.util.Locale,
        requestId: String
    ): AiAdviceResponse {
        val prompt = buildAdviceSectionPrompt(
            profile = profile,
            locale = locale,
            localeTag = localeTag
        )
        val sectionRequestId = "$requestId|sleep"
        val generated = llmRepairer.generate(
            logger = logger,
            operation = "sleep-section",
            requestId = sectionRequestId,
            basePrompt = prompt,
            callModel = { currentPrompt -> generateWithFallback(profile, currentPrompt, "sleep-section", sectionRequestId) },
            strategy = AiAdviceResponse.serializer(),
            validator = SchemaValidator { advice ->
                val normalized = normalizeAdvice(advice)
                validateSleepAdvice(normalized)?.let(::listOf) ?: emptyList()
            },
            extractionMode = ExtractionMode.FirstJsonObject
        )
        return normalizeAdvice(generated)
    }

    private fun buildTrainingSectionPrompt(
        profile: AiProfile,
        weekIndex: Int,
        locale: java.util.Locale,
        localeTag: String,
        measurementSystem: String,
        weightUnit: String
    ): String {
        val languageDisplay = locale.getDisplayLanguage(locale).ifBlank { locale.language.ifBlank { "English" } }
        val profileJson = json.encodeToString(AiProfile.serializer(), profile)
        return buildString {
            appendLine("You are an elite strength coach.")
            appendLine("User locale: $languageDisplay ($localeTag). Reply with JSON only.")
            appendLine("Measurement system: $measurementSystem. Use $weightUnit for load.")
            appendLine("Return ONLY this JSON schema:")
            appendLine("{\"weekIndex\": Int, \"workouts\": [{\"id\": String, \"date\": \"YYYY-MM-DD\", \"sets\": [{\"exerciseId\": String, \"reps\": Int, \"weightKg\": Double?, \"rpe\": Double?}]}]}")
            appendLine("Allowed exerciseId values: [squat, bench, deadlift, ohp, row, pullup, lunge, dip, pushup, curl, tricep_extension, plank, hip_thrust, leg_press].")
            appendLine("Max 5 workouts, max 6 sets per workout, no duplicate workout IDs.")
            appendLine("Plan for weekIndex=$weekIndex and use valid ISO dates in that week.")
            appendLine("PROFILE JSON:")
            append(profileJson)
        }
    }

    private fun buildNutritionSectionPrompt(
        profile: AiProfile,
        weekIndex: Int,
        locale: java.util.Locale,
        localeTag: String,
        measurementSystem: String,
        weightUnit: String
    ): String {
        val languageDisplay = locale.getDisplayLanguage(locale).ifBlank { locale.language.ifBlank { "English" } }
        val profileJson = json.encodeToString(AiProfile.serializer(), profile)
        val restrictionsSummary = nutritionRestrictionsPrompt(profile)
        return buildString {
            appendLine("You are an elite sports nutritionist.")
            appendLine("User locale: $languageDisplay ($localeTag). Reply in this language.")
            appendLine("Measurement system: $measurementSystem. Bodyweight unit: $weightUnit.")
            appendLine("Return ONLY this JSON schema:")
            appendLine("{\"weekIndex\": Int, \"mealsByDay\": {\"Mon\":[{\"name\": String, \"ingredients\": [String], \"kcal\": Int, \"macros\": {\"proteinGrams\": Int, \"fatGrams\": Int, \"carbsGrams\": Int, \"kcal\": Int}}], \"Tue\": [...], \"Wed\": [...], \"Thu\": [...], \"Fri\": [...], \"Sat\": [...], \"Sun\": [...]}, \"shoppingList\": [String]}")
            appendLine("Nutrition hard rules:")
            appendLine("- Cover Mon..Sun with required day keys.")
            appendLine("- Meals/day by goal: lose -> 3-4, maintain -> 3-5, gain -> 4-6.")
            appendLine("- Each meal needs integer macros and kcal aligned with 4*protein + 4*carbs + 9*fat (+/-20).")
            appendLine("- Avoid duplicate meals within the same day.")
            appendLine(restrictionsSummary)
            appendLine("PROFILE JSON:")
            appendLine(profileJson)
            appendLine("weekIndex=$weekIndex")
        }
    }

    private fun buildAdviceSectionPrompt(
        profile: AiProfile,
        locale: java.util.Locale,
        localeTag: String
    ): String {
        val languageDisplay = locale.getDisplayLanguage(locale).ifBlank { locale.language.ifBlank { "English" } }
        val profileJson = json.encodeToString(AiProfile.serializer(), profile)
        return buildString {
            appendLine("You are a recovery and sleep coach.")
            appendLine("User locale: $languageDisplay ($localeTag). Reply in this language.")
            appendLine("Return ONLY this JSON schema:")
            appendLine("{\"messages\": [String], \"disclaimer\": String}")
            appendLine("Provide 5-7 concise, practical tips.")
            appendLine("PROFILE JSON:")
            append(profileJson)
        }
    }

    private suspend fun loadCachedBundle(requestId: String): AiBootstrapResponse? {
        val now = System.currentTimeMillis()
        return cacheMutex.withLock {
            bundleCache[requestId]?.let { entry ->
                if (isFresh(entry, now)) {
                    entry.bundle
                } else {
                    bundleCache.remove(requestId)
                    null
                }
            }
        }
    }

    private suspend fun lockInFlightBundle(requestId: String): PendingBundle {
        return cacheMutex.withLock {
            bundleCache[requestId]?.let { entry ->
                if (isFresh(entry)) {
                    return@withLock PendingBundle(
                        deferred = CompletableDeferred<AiBootstrapResponse>().apply { complete(entry.bundle) },
                        isOwner = false
                    )
                }
                bundleCache.remove(requestId)
            }

            val existing = inFlightBundles[requestId]
            if (existing != null) {
                PendingBundle(deferred = existing, isOwner = false)
            } else {
                val created = CompletableDeferred<AiBootstrapResponse>()
                inFlightBundles[requestId] = created
                PendingBundle(deferred = created, isOwner = true)
            }
        }
    }

    private fun validateBundle(
        bundle: AiBootstrapResponse,
        profile: AiProfile,
        locale: java.util.Locale
    ): List<String> {
        val errors = mutableListOf<String>()

        val training = bundle.trainingPlan
        if (training == null) {
            errors += "trainingPlan was null"
        } else {
            validateTrainingPlan(training)?.let { errors += "trainingPlan: $it" }
        }

        val nutrition = bundle.nutritionPlan
        if (nutrition == null) {
            errors += "nutritionPlan was null"
        } else {
            errors += validateNutritionPlan(nutrition, profile, locale).map { "nutritionPlan: $it" }
        }

        val advice = bundle.sleepAdvice
        if (advice == null) {
            errors += "sleepAdvice was null"
        } else {
            validateSleepAdvice(advice)?.let { errors += "sleepAdvice: $it" }
        }

        return errors.distinct()
    }

    private fun cacheKey(profile: AiProfile, weekIndex: Int, localeTag: String): String {
        val fingerprint = json.encodeToString(AiProfile.serializer(), profile)
        return "${fingerprint.hashCode()}|$weekIndex|$localeTag"
    }

    private suspend fun generateWithFallback(
        profile: AiProfile,
        prompt: String,
        operation: String,
        requestId: String
    ): String {
        val mode = profile.llmMode?.trim()?.lowercase()
        if (mode == "free") return freeLlmClient.generateJson(prompt)

        return runCatching {
            paidLlmClient.generateJson(prompt)
        }.recoverCatching { ex ->
            if (!shouldFallbackToFree(ex)) throw ex
            logger.warn(
                "LLM {} switching to free client requestId={} reason={}",
                operation,
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

    private fun isFresh(entry: CacheEntry, now: Long = System.currentTimeMillis()): Boolean =
        now - entry.timestamp <= entry.ttlMs

    private fun shouldAttemptDecomposedGeneration(error: Throwable): Boolean {
        if (error is RateLimitException) return false
        val message = error.message?.lowercase().orEmpty()
        if (message.contains(" 401") || message.contains("unauthorized")) return false
        if (message.contains(" 402") || message.contains("insufficient credits") || message.contains("payment required")) return false
        if (message.contains(" 403") || message.contains("forbidden")) return false
        if (message.contains(" 429") || message.contains("rate limit")) return false
        return true
    }

    companion object {
        private val LlmTimeoutMs = Env["AI_LLM_TIMEOUT_MS"]?.toLongOrNull()?.coerceAtLeast(30_000L) ?: 180_000L
        private val EnableDecomposedBundle = Env["AI_DECOMPOSE_BUNDLE_ON_FAILURE"]
            ?.equals("true", ignoreCase = true)
            ?: true
        private val PreferDecomposedBundleFirst = Env["AI_PREFER_DECOMPOSED_BUNDLE_FIRST"]
            ?.equals("true", ignoreCase = true)
            ?: true
        private const val DefaultLocale = "en-US"
        private const val BundleCacheTtlMs = 30 * 60 * 1000L
        private const val FallbackCacheTtlMs = 2 * 60 * 1000L

        private val bundleCache = ConcurrentHashMap<String, CacheEntry>()
        private val cacheMutex = Mutex()
        private val inFlightBundles = ConcurrentHashMap<String, CompletableDeferred<AiBootstrapResponse>>()
    }

    private data class CacheEntry(
        val bundle: AiBootstrapResponse,
        val timestamp: Long,
        val ttlMs: Long
    )
    private data class PendingBundle(
        val deferred: CompletableDeferred<AiBootstrapResponse>,
        val isOwner: Boolean
    )
}
