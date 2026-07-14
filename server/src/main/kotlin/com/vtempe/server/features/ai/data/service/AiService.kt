package com.vtempe.server.features.ai.data.service

import com.vtempe.server.config.Env
import com.vtempe.server.features.ai.data.llm.LLMClient
import com.vtempe.server.features.ai.data.llm.LlmException
import com.vtempe.server.features.ai.data.llm.LlmRepairer
import com.vtempe.server.features.ai.data.llm.decode.SchemaValidator
import com.vtempe.server.features.ai.data.llm.pipeline.ExtractionMode
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

        val cached = bundleCache.loadIfFresh(requestId)
        if (cached != null) return cached

        val pendingState = bundleCache.lockInFlight(requestId)
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
                bundleCache.store(requestId, decomposed, BundleCache.BundleCacheTtlMs)
                return decomposed
            }
            logger.warn("Decomposed-first bundle generation failed, falling back to monolithic requestId={}", requestId)
        }

        val prompt = buildBundlePrompt(
            json = json,
            locale = locale,
            measurementSystem = measurementSystem,
            weightUnit = weightUnit,
            request = AiBootstrapRequest(profile, weekIndex, localeTag),
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
                callModel = { currentPrompt -> generateWithFallback(profile, currentPrompt, "coach-bundle", requestId) },
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
                            skeletonViolations.joinToString(" | ")
                        )
                        AiQualityMetrics.recordValidation(logger, "coach-bundle-skeleton", requestId, skeletonViolations)
                    }

                    val normalizedCandidate = normalizeBundle(bundle, locale, profile, trainingPlanResolver, enforcedWeekIndex = weekIndex)
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
                            warningErrors.joinToString(" | ")
                        )
                    }
                    criticalErrors
                },
                extractionMode = ExtractionMode.FirstJsonObject,
                feedbackSuffix = restrictionsFeedback,
            )

            val normalized = normalizeBundle(generated, locale, profile, trainingPlanResolver, enforcedWeekIndex = weekIndex)
            bundleCache.store(requestId, normalized, BundleCache.BundleCacheTtlMs)
            normalized
        } catch (t: Throwable) {
            // Same narrowing as runWithFallback (see LlmFallbackPolicy.isExpectedLlmFailure) —
            // this catch is the REAL swallow point for training()/nutrition()/sleep()/bundle():
            // fetchBundle never lets a caught exception escape past this point (it always
            // returns a fallback bundle below), so runWithFallback's own narrowing is live
            // only for whatever throws BEFORE this try block. Without this check here too, a
            // genuine bug (NPE in normalizeBundle, a broken validator, etc.) would silently
            // become "the AI must have been slow" for every one of those four entry points.
            if (t is CancellationException && t !is TimeoutCancellationException) {
                bundleCache.fail(requestId, t)
                throw t
            }
            if (!isExpectedLlmFailure(t)) {
                logger.error("Unexpected error generating coach bundle requestId={} — not falling back, surfacing as failure", requestId, t)
                bundleCache.fail(requestId, t)
                throw t
            }
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
                bundleCache.store(requestId, decomposed, BundleCache.BundleCacheTtlMs)
                return decomposed
            }

            val fallbackBundle = normalizeBundle(
                AiBootstrapResponse(
                    trainingPlan = fallbackTraining(AiTrainingRequest(profile, weekIndex, localeTag)),
                    nutritionPlan = fallbackNutrition(AiNutritionRequest(profile, weekIndex, localeTag)),
                    sleepAdvice = fallbackAdvice(AiAdviceRequest(profile, localeTag))
                ),
                locale,
                profile,
                trainingPlanResolver
            )
            logger.warn("LLM bundle fallback activated for requestId={}", requestId, t)
            AiQualityMetrics.recordFallback(logger, "coach-bundle", t)
            bundleCache.store(requestId, fallbackBundle, BundleCache.FallbackCacheTtlMs)
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
                    profile,
                    trainingPlanResolver,
                    enforcedWeekIndex = weekIndex
                )
                val errors = validateBundle(exerciseCatalog, bundle, profile, locale)
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
            json = json,
            exerciseCatalog = exerciseCatalog,
            trainingPlanResolver = trainingPlanResolver,
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
                val normalized = normalizeTrainingPlan(plan, profile, trainingPlanResolver, enforcedWeekIndex = weekIndex)
                validateTrainingPlan(normalized, exerciseCatalog, profile?.injuries.orEmpty())?.let(::listOf) ?: emptyList()
            },
            extractionMode = ExtractionMode.FirstJsonObject
        )
        return normalizeTrainingPlan(generated, profile, trainingPlanResolver, enforcedWeekIndex = weekIndex)
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

    // SHA-256, not String.hashCode() (32-bit — collisions are a real risk at scale and would
    // hand one user another user's cached training/nutrition plan). internal, not private, so
    // PersonalizationTest can assert distinct profiles never collide.
    internal fun cacheKey(profile: AiProfile, weekIndex: Int, localeTag: String): String {
        val fingerprint = json.encodeToString(AiProfile.serializer(), profile)
        val digest = MessageDigest.getInstance("SHA-256").digest(fingerprint.toByteArray(Charsets.UTF_8))
        val hex = digest.joinToString("") { "%02x".format(it) }
        return "$hex|$weekIndex|$localeTag"
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

    // shouldFallbackToFree(): shared with ChatService, see LlmFallbackPolicy.kt.

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
