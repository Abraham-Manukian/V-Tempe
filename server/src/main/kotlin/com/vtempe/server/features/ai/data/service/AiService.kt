package com.vtempe.server.features.ai.data.service

import com.vtempe.server.config.Env
import com.vtempe.server.features.ai.data.llm.LLMClient
import com.vtempe.server.features.ai.data.llm.LlmRepairer
import com.vtempe.server.features.ai.data.llm.RateLimitException
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
import com.vtempe.server.features.ai.data.service.split.TrainingSplitPlanner
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
    private val llmRepairer: LlmRepairer,
    private val exerciseCatalog: ExerciseCatalog,
    private val trainingPlanResolver: TrainingPlanResolver
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
                        validateSkeletonCompliance(it, profile, weekIndex)
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
                extractionMode = ExtractionMode.FirstJsonObject,
                feedbackSuffix = restrictionsFeedback,
            )

            val normalized = normalizeBundle(generated, locale, profile, trainingPlanResolver, enforcedWeekIndex = weekIndex)
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
                profile,
                trainingPlanResolver
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
                    profile,
                    trainingPlanResolver,
                    enforcedWeekIndex = weekIndex
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
        val trainingResolverDescriptions = buildTrainingResolverDescriptions(
            exerciseCatalog = exerciseCatalog,
            trainingPlanResolver = trainingPlanResolver,
            trainingModeRaw = profile.trainingMode,
            equipment = profile.equipment
        )
        val today = java.time.LocalDate.now()
        val workoutDates = computeWorkoutDatesForWeek(profile.weeklySchedule, today)

        val trainingDays = if (workoutDates.isNotEmpty()) workoutDates
            else listOf("Mon","Tue","Wed","Thu","Fri","Sat","Sun").filter { profile.weeklySchedule[it] == true }
        val skeletons = TrainingSplitPlanner.build(
            trainingDays        = trainingDays,
            focusRaw            = profile.trainingFocus,
            goalRaw             = profile.goal,
            splitPreferenceRaw  = profile.splitPreference,
            experienceLevel     = profile.experienceLevel,
            age                 = profile.age,
            sexRaw              = profile.sex,
            lifestyleRaw        = profile.lifestyleActivity,
            injuries            = profile.injuries,
            sessionDurationMins = profile.sessionDurationMins,
            weekIndex           = weekIndex,
            hasHistory          = profile.recentWorkouts.isNotEmpty()
        )
        val resolvedExercises = skeletons.mapIndexed { si, skeleton ->
            val used = mutableSetOf<String>()
            skeleton.slots.mapIndexed { j, slot ->
                val id = trainingPlanResolver.resolveExerciseId(
                    rawToken            = slot.pattern.token,
                    trainingModeRaw     = profile.trainingMode,
                    equipment           = profile.equipment,
                    usedExerciseIds     = used,
                    rotationSeed        = si * 31 + j,
                    userExperienceLevel = profile.experienceLevel
                )
                if (id != null) used += id
                id
            }
        }

        return buildString {
            appendLine("You are an elite strength coach.")
            appendLine("User locale: $languageDisplay ($localeTag). Reply with JSON only.")
            appendLine("TODAY'S DATE: $today — use this as the reference.")
            appendLine("Measurement system: $measurementSystem. Use $weightUnit for load.")
            appendLine("If PROFILE JSON contains recentWorkouts, use it to progress, hold, or regress load, volume, and exercise difficulty.")
            if (workoutDates.isNotEmpty()) {
                appendLine("MANDATORY workout dates — use EXACTLY these ISO dates, one workout per date, no other dates allowed:")
                appendLine("  ${workoutDates.joinToString(", ")}")
            } else {
                appendLine("Plan for weekIndex=$weekIndex starting from $today. All workout dates must be >= $today.")
            }
            appendLine("Return ONLY this JSON schema:")
            appendLine("{\"weekIndex\": Int, \"workouts\": [{\"id\": String, \"label\": String, \"date\": \"YYYY-MM-DD\", \"sets\": [{\"exerciseId\": String, \"reps\": Int, \"weightKg\": Double?, \"rpe\": Double?}]}]}")
            appendLine()
            appendLine(TrainingSplitPlanner.renderPromptBlock(skeletons, resolvedExercises))
            appendLine()
            appendLine("DURATION EXERCISES — use `reps` field for time, NOT repetition count:")
            appendLine("- plank, side_plank, wall_sit, l_sit, hollow_body: `reps` = seconds (e.g. reps=30 means 30s hold). Range: 20–120.")
            appendLine("- bike/stationary_bike/cycling, mountain_climber: `reps` = seconds. Range: 60–600.")
            appendLine("- run/treadmill: `reps` = minutes. Range: 5–60.")
            appendLine("- NEVER set reps=8 for a plank. Set reps=30 for a 30-second plank.")
            appendLine("- For all duration exercises: set weightKg = null.")
            appendLine()
            appendLine("WEIGHT ASSIGNMENT RULES (CRITICAL):")
            appendLine("- Bodyweight exercises (pullup, chin_up, wide_pullup, pushup, dip, plank, lunge, nordic_curl, muscle_up): set weightKg = null.")
            appendLine("- Barbell exercises (squat, bench_press, deadlift, rdl, barbell_row, overhead_press): assign realistic starting weights.")
            appendLine("  Beginner male: ~60kg squat, ~50kg bench, ~70kg deadlift. Scale ±20% per experience level.")
            appendLine("  Beginner female: ~30kg squat, ~25kg bench, ~40kg deadlift.")
            appendLine("- Dumbbell exercises: use per-dumbbell weight (e.g. 15.0 for 15kg dumbbells).")
            appendLine("- NEVER assign the same weightKg to a barbell compound AND a bodyweight exercise.")
            appendLine()
            appendLine(trainingResolverDescriptions)
            appendLine("Max 5 workouts, max 6 sets per workout, no duplicate workout IDs.")
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
        val targets = computeTargetNutrition(profile)
        return buildString {
            appendLine("You are an elite sports nutritionist.")
            appendLine("User locale: $languageDisplay ($localeTag). Reply in this language.")
            appendLine("Measurement system: $measurementSystem. Bodyweight unit: $weightUnit.")
            appendLine()
            appendLine(restrictionsSummary)
            appendLine()
            appendLine("PRE-COMPUTED TARGETS — use EXACTLY these values, do NOT recalculate:")
            appendLine("  Daily kcal: ${targets.kcal}  |  Protein: ${targets.proteinG}g  |  Fat: ${targets.fatG}g  |  Carbs: ${targets.carbsG}g")
            appendLine("The sum of all meals every day MUST be within ±5% of these targets.")
            if (profile.dietaryPreferences.isNotEmpty()) {
                appendLine("PREFERRED FOODS (include these in meals where possible, they are likes — not restrictions): ${profile.dietaryPreferences.joinToString(", ")}")
            }
            appendLine()
            appendLine("Return ONLY this JSON schema:")
            appendLine("{\"weekIndex\": Int, \"mealsByDay\": {\"Mon\":[{\"name\": String, \"ingredients\": [String], \"recipe\": String, \"allergenTags\": [String], \"kcal\": Int, \"macros\": {\"proteinGrams\": Int, \"fatGrams\": Int, \"carbsGrams\": Int, \"kcal\": Int}}], \"Tue\": [...], \"Wed\": [...], \"Thu\": [...], \"Fri\": [...], \"Sat\": [...], \"Sun\": [...]}, \"shoppingList\": [String]}")
            appendLine("Nutrition hard rules:")
            appendLine("- Cover Mon..Sun with required day keys.")
            appendLine("- Meals/day by goal: lose -> 3-4, maintain -> 3-5, gain -> 4-6.")
            appendLine("- Each meal needs integer macros and kcal aligned with 4*protein + 4*carbs + 9*fat (+/-20).")
            appendLine("- Avoid duplicate meals within the same day.")
            appendLine("- INGREDIENTS: always include quantity + unit for each ingredient (e.g. \"150г гречки\", \"200мл кефира\", \"2 яйца\"). Never list bare ingredient names without amounts.")
            appendLine("- RECIPE: provide a short 2-4 step cooking instruction in $languageDisplay. Steps concise — one action each. Do NOT include nutritional commentary.")
            appendLine("- Example meal: {\"name\":\"Овсянка\",\"ingredients\":[\"150г овсяных хлопьев\",\"250мл молока\"],\"recipe\":\"1. Залей хлопья молоком. 2. Вари 5 мин.\",\"kcal\":360,\"macros\":{\"proteinGrams\":10,\"fatGrams\":6,\"carbsGrams\":64,\"kcal\":358}}")
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

    /**
     * Checks that each exercise in the raw AI response belongs to the expected movement pattern
     * for that skeleton slot. Returns specific, actionable error messages fed back to the LLM
     * so it can self-correct. Normalization still acts as a safety net after all retries.
     */
    private fun validateSkeletonCompliance(
        plan: AiTrainingResponse,
        profile: AiProfile,
        weekIndex: Int
    ): List<String> {
        val trainingDays = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
            .filter { profile.weeklySchedule[it] == true }
        if (trainingDays.isEmpty()) return emptyList()
        val skeletons = runCatching {
            TrainingSplitPlanner.build(
                trainingDays        = trainingDays,
                focusRaw            = profile.trainingFocus,
                goalRaw             = profile.goal,
                splitPreferenceRaw  = profile.splitPreference,
                experienceLevel     = profile.experienceLevel,
                age                 = profile.age,
                sexRaw              = profile.sex,
                lifestyleRaw        = profile.lifestyleActivity,
                injuries            = profile.injuries,
                sessionDurationMins = profile.sessionDurationMins,
                weekIndex           = weekIndex
            )
        }.getOrDefault(emptyList())
        if (skeletons.isEmpty()) return emptyList()

        val mode = trainingPlanResolver.resolveMode(profile.trainingMode, profile.equipment)
        val equipment = trainingPlanResolver.normalizeEquipment(profile.equipment)
        val errors = mutableListOf<String>()

        plan.workouts.forEachIndexed { wi, workout ->
            val skeleton = skeletons.getOrNull(wi) ?: return@forEachIndexed
            val sessionLabel = skeleton.label.let {
                val dash = it.indexOf(" — "); if (dash >= 0) it.substring(dash + 3) else it
            }
            workout.sets.forEachIndexed { si, set ->
                val expectedSlot = skeleton.slots.getOrNull(si) ?: return@forEachIndexed
                val token = normalizeExerciseToken(set.exerciseId)
                // Pattern tokens (pattern:*) are allowed — resolver picks the exercise
                if (token.startsWith("pattern:")) return@forEachIndexed
                val catalogItem = exerciseCatalog.findByIdOrAlias(token) ?: return@forEachIndexed
                if (catalogItem.primaryPattern != expectedSlot.pattern) {
                    val examples = exerciseCatalog.candidatesFor(expectedSlot.pattern, mode, equipment)
                        .take(3).joinToString(", ") { it.id }
                    errors += "workout[$wi] ($sessionLabel) slot ${si + 1}: " +
                        "expected ${expectedSlot.pattern.id} (${expectedSlot.pattern.promptDescription}) " +
                        "but got '${catalogItem.id}' which is ${catalogItem.primaryPattern.id}. " +
                        "Use one of: $examples"
                }
            }
        }
        return errors
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
            validateTrainingPlan(training, exerciseCatalog, profile.injuries)?.let { errors += "trainingPlan: $it" }
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
