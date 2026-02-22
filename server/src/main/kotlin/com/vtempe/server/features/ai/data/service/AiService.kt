package com.vtempe.server.features.ai.data.service

import com.vtempe.server.config.Env
import com.vtempe.server.features.ai.data.llm.LLMClient
import com.vtempe.server.features.ai.data.llm.LlmRepairer
import com.vtempe.server.features.ai.data.llm.decode.SchemaValidator
import com.vtempe.server.features.ai.data.llm.pipeline.ExtractionMode
import com.vtempe.server.shared.dto.advice.AiAdviceRequest
import com.vtempe.server.shared.dto.bootstrap.AiBootstrapRequest
import com.vtempe.server.shared.dto.bootstrap.AiBootstrapResponse
import com.vtempe.server.shared.dto.profile.AiProfile
import com.vtempe.server.shared.dto.training.AiTrainingRequest
import com.vtempe.server.shared.dto.nutrition.AiNutritionRequest
import java.util.concurrent.ConcurrentHashMap
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
        val prompt = buildBundlePrompt(
            json = json,
            locale = locale,
            measurementSystem = measurementSystem,
            weightUnit = if (measurementSystem.startsWith("imperial")) "pounds" else "kilograms",
            request = AiBootstrapRequest(profile, weekIndex, localeTag)
        )

        return try {
            val generated = llmRepairer.generate(
                logger = logger,
                operation = "coach-bundle",
                requestId = requestId,
                basePrompt = prompt,
                callModel = llmClient::generateJson,
                strategy = AiBootstrapResponse.serializer(),
                validator = SchemaValidator { bundle ->
                    val normalizedCandidate = normalizeBundle(bundle, locale)
                    validateBundle(normalizedCandidate)?.let(::listOf) ?: emptyList()
                },
                extractionMode = ExtractionMode.FirstJsonObject
            )

            val normalized = normalizeBundle(generated, locale)
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
            val fallbackBundle = normalizeBundle(
                AiBootstrapResponse(
                    trainingPlan = fallbackTraining(AiTrainingRequest(profile, weekIndex, localeTag)),
                    nutritionPlan = fallbackNutrition(AiNutritionRequest(profile, weekIndex, localeTag)),
                    sleepAdvice = fallbackAdvice(AiAdviceRequest(profile, localeTag))
                ),
                locale
            )
            logger.warn("LLM bundle fallback activated for requestId={}", requestId, t)
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

    private fun validateBundle(bundle: AiBootstrapResponse): String? {
        val training = bundle.trainingPlan ?: return "trainingPlan was null"
        validateTrainingPlan(training)?.let { return "trainingPlan: $it" }

        val nutrition = bundle.nutritionPlan ?: return "nutritionPlan was null"
        validateNutritionPlan(nutrition)?.let { return "nutritionPlan: $it" }

        val advice = bundle.sleepAdvice ?: return "sleepAdvice was null"
        validateSleepAdvice(advice)?.let { return "sleepAdvice: $it" }

        return null
    }

    private fun cacheKey(profile: AiProfile, weekIndex: Int, localeTag: String): String {
        val fingerprint = json.encodeToString(AiProfile.serializer(), profile)
        return "${fingerprint.hashCode()}|$weekIndex|$localeTag"
    }

    private fun isFresh(entry: CacheEntry, now: Long = System.currentTimeMillis()): Boolean =
        now - entry.timestamp <= entry.ttlMs

    companion object {
        private val LlmTimeoutMs = Env["AI_LLM_TIMEOUT_MS"]?.toLongOrNull()?.coerceAtLeast(30_000L) ?: 240_000L
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
