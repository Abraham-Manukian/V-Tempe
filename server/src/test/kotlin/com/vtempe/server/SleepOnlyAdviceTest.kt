package com.vtempe.server

import com.vtempe.server.features.ai.data.catalog.BuiltInExerciseCatalog
import com.vtempe.server.features.ai.data.llm.LLMClient
import com.vtempe.server.features.ai.data.llm.LlmRepairer
import com.vtempe.server.features.ai.data.llm.decode.Decoder
import com.vtempe.server.features.ai.data.llm.extract.ResponseExtractor
import com.vtempe.server.features.ai.data.llm.feedback.FeedbackComposer
import com.vtempe.server.features.ai.data.llm.pipeline.LlmPipeline
import com.vtempe.server.features.ai.data.llm.pipeline.PipelineConfig
import com.vtempe.server.features.ai.data.llm.repair.JsonSanitizer
import com.vtempe.server.features.ai.data.llm.schema.ResponseSchema
import com.vtempe.server.features.ai.data.llm.telemetry.LlmErrorTracker
import com.vtempe.server.features.ai.data.llm.telemetry.LlmRawStore
import com.vtempe.server.features.ai.data.resolver.DefaultTrainingPlanResolver
import com.vtempe.server.features.ai.data.service.AiService
import com.vtempe.server.features.ai.data.service.BundleCache
import com.vtempe.server.shared.dto.advice.AiAdviceRequest
import com.vtempe.server.shared.dto.profile.AiProfile
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.util.Collections
import java.util.concurrent.atomic.AtomicInteger

/** VT-AUD-050: a sleep request generates the sleep advice and nothing else. */
class SleepOnlyAdviceTest {

    /** Answers every prompt with sleep tips; a training or nutrition section could never validate. */
    private class AdviceOnlyLlm : LLMClient {
        val calls = AtomicInteger()
        val prompts: MutableList<String> = Collections.synchronizedList(mutableListOf())
        var gate: CompletableDeferred<Unit>? = null
        override suspend fun generateJson(prompt: String, schema: ResponseSchema?): String {
            calls.incrementAndGet()
            prompts += prompt
            gate?.await()
            return """{"messages":["Keep a fixed wake time.","Dim the lights an hour before bed."],"disclaimer":"Not medical advice"}"""
        }
    }

    private fun service(llm: LLMClient): AiService {
        val pipeline = LlmPipeline(
            PipelineConfig(), ResponseExtractor(), JsonSanitizer(), Decoder(Json { ignoreUnknownKeys = true }),
            FeedbackComposer(), LlmRawStore(enabled = false), LlmErrorTracker()
        )
        val catalog = BuiltInExerciseCatalog()
        return AiService(llm, llm, LlmRepairer(pipeline), catalog, DefaultTrainingPlanResolver(catalog), BundleCache())
    }

    private val request = AiAdviceRequest(
        AiProfile(
            age = 30, sex = "MALE", heightCm = 180, weightKg = 80.0, goal = "MAINTAIN", experienceLevel = 2,
            weeklySchedule = mapOf("Mon" to true, "Wed" to true, "Fri" to true),
        ),
        "en",
    )

    @Test
    fun `a cold sleep request makes one advice call and no plan calls`() = runBlocking<Unit> {
        val llm = AdviceOnlyLlm()
        val advice = service(llm).sleep(request)

        assertEquals(listOf("Keep a fixed wake time.", "Dim the lights an hour before bed."), advice.messages)
        assertEquals(1, llm.calls.get(), "prompts sent: ${llm.prompts.map { it.take(80) }}")
    }

    @Test
    fun `concurrent and repeated sleep requests share one generation`() = runBlocking<Unit> {
        val llm = AdviceOnlyLlm().apply { gate = CompletableDeferred() }
        val ai = service(llm)
        val answers = List(3) { async { ai.sleep(request) } }
        llm.gate!!.complete(Unit)
        answers.awaitAll()
        ai.sleep(request) // served from the cache
        assertEquals(1, llm.calls.get())
    }
}
