package com.vtempe.server.app.di

import com.vtempe.server.config.Env
import com.vtempe.server.features.ai.data.llm.LLMClient
import com.vtempe.server.features.ai.data.llm.LlmRepairer
import com.vtempe.server.features.ai.data.llm.OpenRouterLLMClient
import com.vtempe.server.features.ai.data.llm.RetryingLLMClient
import com.vtempe.server.features.ai.data.llm.StubLLMClient
import com.vtempe.server.features.ai.data.llm.ThrottledLLMClient
import com.vtempe.server.features.ai.data.llm.decode.Decoder
import com.vtempe.server.features.ai.data.llm.extract.ResponseExtractor
import com.vtempe.server.features.ai.data.llm.feedback.FeedbackComposer
import com.vtempe.server.features.ai.data.llm.pipeline.LlmPipeline
import com.vtempe.server.features.ai.data.llm.pipeline.PipelineConfig
import com.vtempe.server.features.ai.data.llm.repair.JsonSanitizer
import com.vtempe.server.features.ai.data.llm.telemetry.LlmErrorTracker
import com.vtempe.server.features.ai.data.llm.telemetry.LlmRawStore
import com.vtempe.server.features.ai.data.catalog.BuiltInExerciseCatalog
import com.vtempe.server.features.ai.data.resolver.DefaultTrainingPlanResolver
import com.vtempe.server.features.ai.data.service.AiService
import com.vtempe.server.features.ai.data.service.ChatService
import com.vtempe.server.features.ai.data.usecase.BootstrapUseCaseImpl
import com.vtempe.server.features.ai.data.usecase.ChatUseCaseImpl
import com.vtempe.server.features.ai.data.usecase.NutritionUseCaseImpl
import com.vtempe.server.features.ai.data.usecase.SleepUseCaseImpl
import com.vtempe.server.features.ai.data.usecase.TrainingUseCaseImpl
import com.vtempe.server.features.ai.domain.usecase.BootstrapUseCase
import com.vtempe.server.features.ai.domain.usecase.ChatUseCase
import com.vtempe.server.features.ai.domain.usecase.NutritionUseCase
import com.vtempe.server.features.ai.domain.usecase.SleepUseCase
import com.vtempe.server.features.ai.domain.usecase.TrainingUseCase
import com.vtempe.server.features.ai.domain.port.ExerciseCatalog
import com.vtempe.server.features.ai.domain.port.TrainingPlanResolver
import kotlinx.serialization.json.Json
import org.koin.core.qualifier.named
import org.koin.dsl.module
import org.slf4j.Logger
import org.slf4j.LoggerFactory

private const val DEFAULT_PAID_MODEL = "openrouter/auto"
private const val DEFAULT_FREE_MODEL = "google/gemma-3-27b-it:free"

val serverModule = module {
    val startupLogger = LoggerFactory.getLogger("V-TempeServer")

    val baseUrl = Env["OPENROUTER_BASE_URL"]?.takeIf { it.isNotBlank() }
    val temperature = Env["OPENROUTER_TEMPERATURE"]?.toDoubleOrNull()
    val siteUrl = Env["OPENROUTER_SITE_URL"]?.takeIf { it.isNotBlank() }
    val appName = Env["OPENROUTER_APP_NAME"]?.takeIf { it.isNotBlank() }
    val requestTimeoutMs = Env["OPENROUTER_REQUEST_TIMEOUT_MS"]?.toLongOrNull() ?: 180_000L
    val socketTimeoutMs = Env["OPENROUTER_SOCKET_TIMEOUT_MS"]?.toLongOrNull() ?: requestTimeoutMs
    val connectTimeoutMs = Env["OPENROUTER_CONNECT_TIMEOUT_MS"]?.toLongOrNull() ?: 20_000L
    val topP = Env["OPENROUTER_TOP_P"]?.toDoubleOrNull()
    val maxTokens = Env["OPENROUTER_MAX_TOKENS"]?.toIntOrNull()
    val retryAttempts = Env["OPENROUTER_RETRY_ATTEMPTS"]?.toIntOrNull()?.coerceAtLeast(1) ?: 2

    val paidKey = Env["OPENROUTER_API_KEY"]?.takeIf { it.isNotBlank() }
    val paidModel = Env["OPENROUTER_MODEL"]?.takeIf { it.isNotBlank() } ?: DEFAULT_PAID_MODEL
    val paidFallbackModels = parseModelList(Env["OPENROUTER_FALLBACK_MODELS"])
    val paidEnableAutoFallback = Env["OPENROUTER_ENABLE_AUTO_FALLBACK"]
        ?.equals("true", ignoreCase = true)
        ?: false

    val freeKey = Env["OPENROUTER_FREE_API_KEY"]?.takeIf { it.isNotBlank() } ?: paidKey
    val freeModel = Env["OPENROUTER_FREE_MODEL"]?.takeIf { it.isNotBlank() } ?: DEFAULT_FREE_MODEL
    val freeFallbackModels = parseModelList(Env["OPENROUTER_FREE_FALLBACK_MODELS"])
    val freeEnableAutoFallback = Env["OPENROUTER_FREE_ENABLE_AUTO_FALLBACK"]
        ?.equals("true", ignoreCase = true)
        ?: false

    single(named("llm-paid")) {
        buildOpenRouterClient(
            startupLogger = startupLogger,
            role = "paid",
            apiKey = paidKey,
            model = paidModel,
            fallbackModels = paidFallbackModels,
            enableAutoFallback = paidEnableAutoFallback,
            baseUrl = baseUrl,
            temperature = temperature,
            siteUrl = siteUrl,
            appName = appName,
            requestTimeoutMs = requestTimeoutMs,
            socketTimeoutMs = socketTimeoutMs,
            connectTimeoutMs = connectTimeoutMs,
            topP = topP,
            maxTokens = maxTokens,
            retryAttempts = retryAttempts
        )
    }

    single(named("llm-free")) {
        if (freeKey == paidKey && freeKey != null) {
            startupLogger.info("OPENROUTER_FREE_API_KEY not set, reusing OPENROUTER_API_KEY for free model")
        }
        buildOpenRouterClient(
            startupLogger = startupLogger,
            role = "free",
            apiKey = freeKey,
            model = freeModel,
            fallbackModels = freeFallbackModels,
            enableAutoFallback = freeEnableAutoFallback,
            baseUrl = baseUrl,
            temperature = temperature,
            siteUrl = siteUrl,
            appName = appName,
            requestTimeoutMs = requestTimeoutMs,
            socketTimeoutMs = socketTimeoutMs,
            connectTimeoutMs = connectTimeoutMs,
            topP = topP,
            maxTokens = maxTokens,
            retryAttempts = retryAttempts
        )
    }

    // Default binding kept for compatibility (paid model).
    single<LLMClient> { get(named("llm-paid")) }

    single { com.vtempe.server.shared.util.JsonProvider.instance }

    // --- Json (serialization) ---
    single {
        Json {
            ignoreUnknownKeys = true
            isLenient = true
            coerceInputValues = true
        }
    }

    // --- Pipeline parts ---
    single { ResponseExtractor() }
    single { JsonSanitizer() }
    single { FeedbackComposer() }
    single { LlmRawStore(enabled = true) }
    single { LlmErrorTracker() }

    single { Decoder(get()) }
    single { PipelineConfig(maxAttempts = 3, enableRawStore = true) }

    single {
        LlmPipeline(
            config = get(),
            extractor = get(),
            sanitizer = get(),
            decoder = get(),
            feedback = get(),
            rawStore = get(),
            tracker = get()
        )
    }

    // --- Repairer (class, not object) ---
    single { LlmRepairer(get()) }
    single<ExerciseCatalog> { BuiltInExerciseCatalog() }
    single<TrainingPlanResolver> { DefaultTrainingPlanResolver(get()) }

    // --- Services ---
    single {
        AiService(
            paidLlmClient = get(named("llm-paid")),
            freeLlmClient = get(named("llm-free")),
            llmRepairer = get(),
            exerciseCatalog = get(),
            trainingPlanResolver = get()
        )
    }
    single {
        ChatService(
            paidLlmClient = get(named("llm-paid")),
            freeLlmClient = get(named("llm-free")),
            llmRepairer = get(),
            aiService = get(),
            exerciseCatalog = get(),
            trainingPlanResolver = get()
        )
    }

    // --- UseCases ---
    single<TrainingUseCase> { TrainingUseCaseImpl(aiService = get()) }
    single<NutritionUseCase> { NutritionUseCaseImpl(aiService = get()) }
    single<SleepUseCase> { SleepUseCaseImpl(aiService = get()) }
    single<ChatUseCase> { ChatUseCaseImpl(chatService = get()) }
    single<BootstrapUseCase> { BootstrapUseCaseImpl(aiService = get()) }
}

private fun parseModelList(raw: String?): List<String> = raw
    ?.split(',', ';', '\n')
    ?.map { it.trim() }
    ?.filter { it.isNotEmpty() }
    ?.distinct()
    .orEmpty()

private fun buildOpenRouterClient(
    startupLogger: Logger,
    role: String,
    apiKey: String?,
    model: String,
    fallbackModels: List<String>,
    enableAutoFallback: Boolean,
    baseUrl: String?,
    temperature: Double?,
    siteUrl: String?,
    appName: String?,
    requestTimeoutMs: Long,
    socketTimeoutMs: Long,
    connectTimeoutMs: Long,
    topP: Double?,
    maxTokens: Int?,
    retryAttempts: Int
): LLMClient {
    if (apiKey == null) {
        startupLogger.warn("OPENROUTER key for {} model is missing; using stubbed responses", role)
        return StubLLMClient("{\"reply\":\"Coach is offline right now. Please configure OPENROUTER_API_KEY.\"}")
    }

    startupLogger.info("Registering OpenRouterLLMClient role={} model={}", role, model)

    val openRouter = OpenRouterLLMClient(
        apiKey = apiKey,
        model = model,
        fallbackModels = fallbackModels,
        enableAutoFallback = enableAutoFallback,
        baseUrl = baseUrl,
        temperature = temperature,
        siteUrl = siteUrl,
        appName = appName,
        requestTimeoutMs = requestTimeoutMs,
        socketTimeoutMs = socketTimeoutMs,
        connectTimeoutMs = connectTimeoutMs,
        topP = topP,
        maxTokens = maxTokens
    )
    val throttled = ThrottledLLMClient(openRouter, minSpacingMs = 2_500)

    return RetryingLLMClient(
        delegate = throttled,
        attempts = retryAttempts,
        initialDelayMs = 2_500,
        maxDelayMs = 15_000,
        backoffMultiplier = 1.8
    )
}
