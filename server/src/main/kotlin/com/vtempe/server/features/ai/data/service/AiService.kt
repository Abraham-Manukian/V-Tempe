package com.vtempe.server.features.ai.data.service

import com.vtempe.server.config.Env
import com.vtempe.server.features.ai.data.llm.LLMClient
import com.vtempe.server.features.ai.data.llm.LlmException
import com.vtempe.server.features.ai.data.llm.LlmRepairer
import com.vtempe.server.features.ai.data.llm.decode.SchemaValidator
import com.vtempe.server.features.ai.data.llm.pipeline.ExtractionMode
import com.vtempe.server.features.ai.data.privacy.boundedForPrompt
import com.vtempe.server.features.ai.data.privacy.withoutDirectIdentifiers
import com.vtempe.server.features.ai.domain.port.ExerciseCatalog
import com.vtempe.server.features.ai.domain.port.TrainingPlanResolver
import com.vtempe.server.shared.dto.advice.AiAdviceRequest
import com.vtempe.server.shared.dto.advice.AiAdviceResponse
import com.vtempe.server.shared.dto.bootstrap.AiBootstrapRequest
import com.vtempe.server.shared.dto.bootstrap.AiBootstrapResponse
import com.vtempe.server.shared.dto.nutrition.AiNutritionResponse
import com.vtempe.server.shared.dto.profile.AiProfile
import com.vtempe.server.shared.dto.training.AiTrainingResponse
import com.vtempe.server.shared.dto.training.AiTrainingRequest
import com.vtempe.server.shared.dto.nutrition.AiNutritionRequest
import java.security.MessageDigest
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory

class AiService(
    private val paidLlmClient: LLMClient,
    private val freeLlmClient: LLMClient,
    private val llmRepairer: LlmRepairer,
    private val exerciseCatalog: ExerciseCatalog,
    private val trainingPlanResolver: TrainingPlanResolver,
    private val bundleCache: BundleCache = BundleCache()
) {

    private val logger = LoggerFactory.getLogger(AiService::class.java)
    private val llmRouter = LlmClientRouter(paidLlmClient, freeLlmClient)

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
    }

    suspend fun training(req: AiTrainingRequest) = runWithFallback(
        operation = "training",
        fallback = { fallbackTraining(req) }
    ) {
        fetchBundle(req.profile, req.weekIndex, req.locale ?: req.profile.locale, req.weekStart).trainingPlan
            ?: throw IllegalStateException("trainingPlan missing in bundle")
    }

    suspend fun nutrition(req: AiNutritionRequest) = runWithFallback(
        operation = "nutrition",
        fallback = { fallbackNutrition(req) }
    ) {
        fetchBundle(req.profile, req.weekIndex, req.locale ?: req.profile.locale, req.weekStart).nutritionPlan
            ?: throw IllegalStateException("nutritionPlan missing in bundle")
    }

    suspend fun sleep(req: AiAdviceRequest) = runWithFallback(
        operation = "sleep",
        fallback = { fallbackAdvice(req) }
    ) {
        fetchSleepAdvice(req.profile, req.locale ?: req.profile.locale)
    }

    suspend fun bundle(req: AiBootstrapRequest): AiBootstrapResponse = runWithFallback(
        operation = "bundle",
        fallback = {
            AiBootstrapResponse(
                trainingPlan = fallbackTraining(AiTrainingRequest(req.profile, req.weekIndex, req.locale, req.weekStart)),
                nutritionPlan = fallbackNutrition(AiNutritionRequest(req.profile, req.weekIndex, req.locale, req.weekStart)),
                sleepAdvice = fallbackAdvice(AiAdviceRequest(req.profile, req.locale))
            )
        }
    ) {
        fetchBundle(req.profile, req.weekIndex, req.locale, req.weekStart)
    }

    private suspend fun <T> runWithFallback(
        operation: String,
        fallback: () -> T,
        block: suspend () -> T
    ): T = runCatching {
        withTimeout(LlmTimeoutMs) { block() }
    }.getOrElse { error ->
        // Real coroutine cancellation (client disconnected, request scope torn down) must
        // propagate, never get swallowed into a fallback response — only the timeout we set
        // ourselves above counts as an expected, recoverable failure.
        if (error is CancellationException && error !is TimeoutCancellationException) throw error
        if (!isExpectedLlmFailure(error)) {
            logger.error("Unexpected error in LLM operation {} — not falling back, surfacing as failure", operation, error)
            throw error
        }
        logger.warn("LLM operation fallback triggered for {}", operation, error)
        AiQualityMetrics.recordFallback(logger, operation, error)
        fallback()
    }

    private suspend fun fetchBundle(
        rawProfile: AiProfile,
        weekIndex: Int,
        localeRaw: String?,
        weekStartRaw: String? = null,
    ): AiBootstrapResponse {
        // Single choke point before any prompt is built: every LLM call below sees only the
        // identifier-free profile (see DirectIdentifierRedactor).
        val profile = rawProfile.boundedForPrompt().withoutDirectIdentifiers()
        val localeTag = localeRaw?.takeIf { it.isNotBlank() } ?: DefaultLocale
        val profileHash = json.encodeToString(AiProfile.serializer(), profile).hashCode()
        val weekStart = resolveWeekStart(weekStartRaw)
        val requestId = cacheKey(profile, weekIndex, localeTag) + "|$weekStart"
        logger.debug(
            "LLM {} requestId={} locale={} weekIndex={} profileHash={}",
            "coach-bundle",
            requestId,
            localeTag,
            weekIndex,
            profileHash
        )

        return sharedGeneration(requestId) {
            generateBundle(profile, weekIndex, localeTag, requestId, weekStart)
        }
    }

    /**
     * Sleep tips alone (VT-AUD-050): only the advice section is generated — a sleep request no
     * longer starts, waits for and throws away a training and a nutrition plan. Deduplicated and
     * cached like a bundle, under its own key.
     */
    private suspend fun fetchSleepAdvice(rawProfile: AiProfile, localeRaw: String?): AiAdviceResponse {
        val profile = rawProfile.boundedForPrompt().withoutDirectIdentifiers()
        val localeTag = localeRaw?.takeIf { it.isNotBlank() } ?: DefaultLocale
        val requestId = "sleep|" + cacheKey(profile, 0, localeTag)
        val result = sharedGeneration(requestId) {
            val advice = generateAdviceSection(profile, localeTag, safeLocale(localeTag), requestId)
            GeneratedBundle(AiBootstrapResponse(sleepAdvice = advice), BundleCache.BundleCacheTtlMs)
        }
        return result.sleepAdvice ?: throw IllegalStateException("sleepAdvice missing")
    }

    /** A fresh cached result for [requestId], the in-flight one, or a new generation owned by this call. */
    private suspend fun sharedGeneration(
        requestId: String,
        generate: suspend () -> GeneratedBundle,
    ): AiBootstrapResponse {
        while (true) {
            bundleCache.loadIfFresh(requestId)?.let { return it }
            val pending = bundleCache.lockInFlight(requestId)
            if (!pending.isOwner) {
                try {
                    return pending.deferred.await()
                } catch (abandoned: BundleCache.GenerationAbandoned) {
                    continue // the owner was cancelled; this request generates instead
                }
            }
            return ownBundleGeneration(requestId, pending.deferred, generate)
        }
    }

    /** A bundle together with how long it may be served from the cache. */
    private class GeneratedBundle(val bundle: AiBootstrapResponse, val ttlMs: Long)

    /**
     * Runs the owner's whole generation — prompt preparation, LLM, decomposed and fallback paths —
     * and finishes the shared in-flight entry exactly once, whatever happens (VT-AUD-007):
     * cached and handed to waiters on success, the same error on failure, and handed back to the
     * waiters on cancellation so one of them generates instead of everybody waiting for nothing.
     */
    private suspend fun ownBundleGeneration(
        requestId: String,
        generation: kotlinx.coroutines.CompletableDeferred<AiBootstrapResponse>,
        generate: suspend () -> GeneratedBundle,
    ): AiBootstrapResponse {
        val result = try {
            generate()
        } catch (cancelled: CancellationException) {
            bundleCache.abandon(requestId, generation)
            throw cancelled
        } catch (error: Throwable) {
            bundleCache.fail(requestId, generation, error)
            throw error
        }
        bundleCache.complete(requestId, generation, result.bundle, result.ttlMs)
        return result.bundle
    }

    private suspend fun generateBundle(
        profile: AiProfile,
        weekIndex: Int,
        localeTag: String,
        requestId: String,
        weekStart: java.time.LocalDate,
    ): GeneratedBundle {
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
                requestId = requestId,
                weekStart = weekStart,
            )
            if (decomposed != null) return GeneratedBundle(decomposed, BundleCache.BundleCacheTtlMs)
            logger.warn("Decomposed-first bundle generation failed, falling back to monolithic requestId={}", requestId)
        }

        val prompt = buildBundlePrompt(
            json = json,
            locale = locale,
            measurementSystem = measurementSystem,
            weightUnit = weightUnit,
            request = AiBootstrapRequest(profile, weekIndex, localeTag, weekStart.toString()),
            exerciseCatalog = exerciseCatalog,
            trainingPlanResolver = trainingPlanResolver
        )

        val restrictionsFeedback = nutritionRestrictionsPrompt(profile)
        return try {
            val generated = llmRepairer.generate(
                logger = logger,
                operation = "coach-bundle",
                requestId = requestId,
                basePrompt = prompt,
                callModel = { currentPrompt ->
                llmRouter.generateWithFallback(
                    logger, profile, currentPrompt, "coach-bundle", requestId,
                    schema = com.vtempe.server.features.ai.data.llm.schema.JsonSchemas.bootstrapBundle(exerciseCatalog)
                )
            },
                strategy = AiBootstrapResponse.serializer(),
                validator = SchemaValidator { bundle ->
                    // Log skeleton compliance violations on the RAW AI response for observability.
                    // These are NOT treated as critical errors — normalizeTrainingPlan enforces
                    // correct exercises as safety net, so we never throw away valid AI weights/reps
                    // just because the AI picked the wrong exercise pattern.
                    val skeletonViolations = bundle.trainingPlan?.let {
                        validateSkeletonCompliance(exerciseCatalog, trainingPlanResolver, it, profile, weekIndex)
                    }.orEmpty()
                    if (skeletonViolations.isNotEmpty()) {
                        logger.info(
                            "LLM coach-bundle requestId={} skeleton violations (auto-corrected by normalizer): {}",
                            requestId,
                            com.vtempe.server.features.ai.data.llm.telemetry.SafeDiagnostics.validationErrors(skeletonViolations)
                        )
                        AiQualityMetrics.recordValidation(logger, "coach-bundle-skeleton", requestId, skeletonViolations)
                    }

                    val normalizedCandidate = normalizeBundle(bundle, locale, profile, trainingPlanResolver, enforcedWeekIndex = weekIndex, weekStart = weekStart)
                    val errors = validateBundle(exerciseCatalog, normalizedCandidate, profile, locale)
                    val criticalErrors = AiQualityErrorPolicy.criticalErrors(errors)
                    if (criticalErrors.isNotEmpty()) {
                        AiQualityMetrics.recordValidation(logger, "coach-bundle", requestId, criticalErrors)
                    }
                    val warningErrors = AiQualityErrorPolicy.warningErrors(errors)
                    if (warningErrors.isNotEmpty()) {
                        logger.info(
                            "LLM coach-bundle requestId={} accepted with relaxed quality warnings={}",
                            requestId,
                            com.vtempe.server.features.ai.data.llm.telemetry.SafeDiagnostics.validationErrors(warningErrors)
                        )
                    }
                    criticalErrors
                },
                extractionMode = ExtractionMode.FirstJsonObject,
                feedbackSuffix = restrictionsFeedback,
            )

            GeneratedBundle(
                normalizeBundle(generated, locale, profile, trainingPlanResolver, enforcedWeekIndex = weekIndex, weekStart = weekStart),
                BundleCache.BundleCacheTtlMs
            )
        } catch (t: Throwable) {
            // Same narrowing as runWithFallback (see LlmFallbackPolicy.isExpectedLlmFailure) —
            // this catch is the REAL swallow point for training()/nutrition()/sleep()/bundle():
            // fetchBundle never lets a caught exception escape past this point (it always
            // returns a fallback bundle below), so runWithFallback's own narrowing is live
            // only for whatever throws BEFORE this try block. Without this check here too, a
            // genuine bug (NPE in normalizeBundle, a broken validator, etc.) would silently
            // become "the AI must have been slow" for every one of those four entry points.
            // The in-flight entry is finished by ownBundleGeneration for every exit, including these.
            if (t is CancellationException && t !is TimeoutCancellationException) throw t
            if (!isExpectedLlmFailure(t)) {
                logger.error("Unexpected error generating coach bundle requestId={} — not falling back, surfacing as failure", requestId, t)
                throw t
            }
            val decomposed = if (!decomposedAttempted && shouldAttemptDecomposedGeneration(t)) {
                attemptDecomposedBundle(
                    profile = profile,
                    weekIndex = weekIndex,
                    localeTag = localeTag,
                    locale = locale,
                    requestId = requestId,
                    weekStart = weekStart,
                )
            } else {
                null
            }
            if (decomposed != null) return GeneratedBundle(decomposed, BundleCache.BundleCacheTtlMs)

            val fallbackBundle = normalizeBundle(
                AiBootstrapResponse(
                    trainingPlan = fallbackTraining(AiTrainingRequest(profile, weekIndex, localeTag, weekStart.toString())),
                    nutritionPlan = fallbackNutrition(AiNutritionRequest(profile, weekIndex, localeTag, weekStart.toString())),
                    sleepAdvice = fallbackAdvice(AiAdviceRequest(profile, localeTag))
                ),
                locale,
                profile,
                trainingPlanResolver
            )
            logger.warn("LLM bundle fallback activated for requestId={}", requestId, t)
            AiQualityMetrics.recordFallback(logger, "coach-bundle", t)
            GeneratedBundle(fallbackBundle, BundleCache.FallbackCacheTtlMs)
        }
    }

    private suspend fun attemptDecomposedBundle(
        profile: AiProfile,
        weekIndex: Int,
        localeTag: String,
        locale: java.util.Locale,
        requestId: String,
        weekStart: java.time.LocalDate,
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
                            requestId = requestId,
                            weekStart = weekStart,
                        )
                    }.onFailure {
                        logger.warn("Decomposed training generation failed requestId={}", requestId, it)
                    }.getOrElse {
                        fallbackTraining(AiTrainingRequest(profile, weekIndex, localeTag, weekStart.toString()))
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
                        fallbackNutrition(AiNutritionRequest(profile, weekIndex, localeTag, weekStart.toString()))
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
                    profile,
                    trainingPlanResolver,
                    enforcedWeekIndex = weekIndex,
                    weekStart = weekStart,
                )
                val errors = validateBundle(exerciseCatalog, bundle, profile, locale)
                val criticalErrors = AiQualityErrorPolicy.criticalErrors(errors)
                if (criticalErrors.isNotEmpty()) {
                    AiQualityMetrics.recordValidation(logger, "coach-bundle-decomposed", requestId, criticalErrors)
                    throw IllegalStateException("Decomposed bundle failed validation: ${com.vtempe.server.features.ai.data.llm.telemetry.SafeDiagnostics.validationErrors(criticalErrors)}")
                }
                val warningErrors = AiQualityErrorPolicy.warningErrors(errors)
                if (warningErrors.isNotEmpty()) {
                    logger.warn(
                        "Decomposed bundle accepted with non-critical quality warnings requestId={} warnings={}",
                        requestId,
                        com.vtempe.server.features.ai.data.llm.telemetry.SafeDiagnostics.validationErrors(warningErrors)
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
        requestId: String,
        weekStart: java.time.LocalDate,
    ): AiTrainingResponse {
        val prompt = buildTrainingSectionPrompt(
            json = json,
            exerciseCatalog = exerciseCatalog,
            trainingPlanResolver = trainingPlanResolver,
            profile = profile,
            weekIndex = weekIndex,
            locale = locale,
            localeTag = localeTag,
            measurementSystem = measurementSystem,
            weightUnit = weightUnit,
            weekStart = weekStart,
        )
        val sectionRequestId = "$requestId|training"
        val generated = llmRepairer.generate(
            logger = logger,
            operation = "training-section",
            requestId = sectionRequestId,
            basePrompt = prompt,
            callModel = { currentPrompt ->
                llmRouter.generateWithFallback(
                    logger, profile, currentPrompt, "training-section", sectionRequestId,
                    schema = com.vtempe.server.features.ai.data.llm.schema.JsonSchemas.trainingPlan(exerciseCatalog)
                )
            },
            strategy = AiTrainingResponse.serializer(),
            validator = SchemaValidator { plan ->
                val normalized = normalizeTrainingPlan(plan, profile, trainingPlanResolver, enforcedWeekIndex = weekIndex, catalog = exerciseCatalog, weekStart = weekStart)
                validateTrainingPlan(normalized, exerciseCatalog, profile?.injuries.orEmpty())?.let(::listOf) ?: emptyList()
            },
            extractionMode = ExtractionMode.FirstJsonObject
        )
        return normalizeTrainingPlan(generated, profile, trainingPlanResolver, enforcedWeekIndex = weekIndex, catalog = exerciseCatalog, weekStart = weekStart)
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
            json = json,
            profile = profile,
            weekIndex = weekIndex,
            locale = locale,
            localeTag = localeTag,
            measurementSystem = measurementSystem,
            weightUnit = weightUnit
        )
        val sectionRequestId = "$requestId|nutrition"
        val restrictionsFeedback = nutritionRestrictionsPrompt(profile)
        val generated = llmRepairer.generate(
            logger = logger,
            operation = "nutrition-section",
            requestId = sectionRequestId,
            basePrompt = prompt,
            callModel = { currentPrompt ->
                llmRouter.generateWithFallback(
                    logger, profile, currentPrompt, "nutrition-section", sectionRequestId,
                    schema = com.vtempe.server.features.ai.data.llm.schema.JsonSchemas.nutritionPlan()
                )
            },
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
                        com.vtempe.server.features.ai.data.llm.telemetry.SafeDiagnostics.validationErrors(warningErrors)
                    )
                }
                criticalErrors
            },
            extractionMode = ExtractionMode.FirstJsonObject,
            feedbackSuffix = restrictionsFeedback,
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
            json = json,
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
            callModel = { currentPrompt ->
                llmRouter.generateWithFallback(
                    logger, profile, currentPrompt, "sleep-section", sectionRequestId,
                    schema = com.vtempe.server.features.ai.data.llm.schema.JsonSchemas.sleepAdvice()
                )
            },
            strategy = AiAdviceResponse.serializer(),
            validator = SchemaValidator { advice ->
                val normalized = normalizeAdvice(advice)
                validateSleepAdvice(normalized)?.let(::listOf) ?: emptyList()
            },
            extractionMode = ExtractionMode.FirstJsonObject
        )
        return normalizeAdvice(generated)
    }

    // SHA-256, not String.hashCode() (32-bit — collisions are a real risk at scale and would
    // hand one user another user's cached training/nutrition plan). internal, not private, so
    // PersonalizationTest can assert distinct profiles never collide.
    internal fun cacheKey(profile: AiProfile, weekIndex: Int, localeTag: String): String {
        val fingerprint = json.encodeToString(AiProfile.serializer(), profile)
        val digest = MessageDigest.getInstance("SHA-256").digest(fingerprint.toByteArray(Charsets.UTF_8))
        val hex = digest.joinToString("") { "%02x".format(it) }
        return "$hex|$weekIndex|$localeTag"
    }

    // generateWithFallback(): shared with ChatService, see LlmClientRouter.kt.

    private fun shouldAttemptDecomposedGeneration(error: Throwable): Boolean = when (error) {
        is LlmException.RateLimited, is LlmException.Auth, is LlmException.PaymentRequired -> false
        else -> {
            // Legacy fallback for anything not covered by LlmException.
            val message = error.message?.lowercase().orEmpty()
            !(message.contains(" 401") || message.contains("unauthorized") ||
                message.contains(" 402") || message.contains("insufficient credits") || message.contains("payment required") ||
                message.contains(" 403") || message.contains("forbidden") ||
                message.contains(" 429") || message.contains("rate limit"))
        }
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
    }
}
