package com.vtempe.server

import com.vtempe.server.features.ai.data.catalog.BuiltInExerciseCatalog
import com.vtempe.server.features.ai.data.llm.LLMClient
import com.vtempe.server.features.ai.data.llm.LlmRepairer
import com.vtempe.server.features.ai.data.llm.decode.Decoder
import com.vtempe.server.features.ai.data.llm.decode.SchemaValidator
import com.vtempe.server.features.ai.data.llm.extract.ResponseExtractor
import com.vtempe.server.features.ai.data.llm.feedback.FeedbackComposer
import com.vtempe.server.features.ai.data.llm.pipeline.LlmPipeline
import com.vtempe.server.features.ai.data.llm.pipeline.LlmPipelineExhaustedException
import com.vtempe.server.features.ai.data.llm.pipeline.PipelineConfig
import com.vtempe.server.features.ai.data.llm.repair.JsonSanitizer
import com.vtempe.server.features.ai.data.llm.schema.ResponseSchema
import com.vtempe.server.features.ai.data.llm.telemetry.LlmErrorTracker
import com.vtempe.server.features.ai.data.llm.telemetry.LlmRawStore
import com.vtempe.server.features.ai.data.privacy.DirectIdentifierRedactor
import com.vtempe.server.features.ai.data.privacy.withoutDirectIdentifiers
import com.vtempe.server.features.ai.data.resolver.DefaultTrainingPlanResolver
import com.vtempe.server.features.ai.data.service.AiService
import com.vtempe.server.features.ai.data.service.ChatService
import com.vtempe.server.features.payments.yookassa.data.YooKassaPayment
import com.vtempe.server.features.payments.yookassa.data.toLedgerPayload
import com.vtempe.server.shared.dto.chat.AiChatMessage
import com.vtempe.server.shared.dto.chat.AiChatRequest
import com.vtempe.server.shared.dto.profile.AiProfile
import com.vtempe.server.shared.dto.profile.AiSleepEntry
import com.vtempe.server.shared.dto.training.AiTrainingRequest
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.FileTime
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import java.util.Collections
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.io.TempDir
import org.slf4j.LoggerFactory

class PrivacyMinimizationTest {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true; coerceInputValues = true }

    // ── DirectIdentifierRedactor ────────────────────────────────────────────────

    @Test
    fun `redacts e-mail, phone numbers and messenger handles`() {
        val redacted = DirectIdentifierRedactor.redact(
            "Ivan, ivan.petrov+fit@mail.ru, +7 (900) 123-45-67, 8 900 123 45 67, 89001234567, tg @ivan_petrov"
        )
        assertFalse("ivan.petrov" in redacted, redacted)
        assertFalse("123-45-67" in redacted, redacted)
        assertFalse("89001234567" in redacted, redacted)
        assertFalse("@ivan_petrov" in redacted, redacted)
        assertTrue(DirectIdentifierRedactor.EMAIL_PLACEHOLDER in redacted)
        assertTrue(DirectIdentifierRedactor.PHONE_PLACEHOLDER in redacted)
        assertTrue(DirectIdentifierRedactor.HANDLE_PLACEHOLDER in redacted)
    }

    @Test
    fun `leaves training and health numbers untouched`() {
        val text = "Knee pain after 3x10 squats at 80 kg, BP 120/80 on 2026-10-08, 8 sets, 1800 kcal, hernia L4-L5"
        assertEquals(text, DirectIdentifierRedactor.redact(text))
    }

    @Test
    fun `profile redaction keeps body data and the health text the coach needs`() {
        val profile = profile().copy(
            injuries = listOf("left knee meniscus, call me +79001234567"),
            healthNotes = listOf("asthma"),
            sleepHistory = listOf(AiSleepEntry(date = "2026-10-01", durationMinutes = 420, notes = "write to me@example.com"))
        )
        val clean = profile.withoutDirectIdentifiers()

        assertEquals(profile.age, clean.age)
        assertEquals(profile.sex, clean.sex)
        assertEquals(profile.heightCm, clean.heightCm)
        assertEquals(profile.weightKg, clean.weightKg)
        assertEquals(listOf("asthma"), clean.healthNotes)
        assertTrue(clean.injuries.single().startsWith("left knee meniscus"))
        assertFalse("79001234567" in clean.injuries.single())
        assertFalse("me@example.com" in clean.sleepHistory.single().notes)
    }

    // ── What actually reaches the LLM provider ──────────────────────────────────

    @Test
    fun `training prompts carry injuries but no direct identifiers`() = runBlocking {
        val llm = CapturingLlmClient()
        val service = aiService(llm)
        val request = AiTrainingRequest(
            profile = profile().copy(injuries = listOf("lower back pain, contact anna@example.com")),
            weekIndex = 0
        )

        service.training(request)

        assertTrue(llm.prompts.isNotEmpty(), "expected at least one LLM call")
        llm.prompts.forEach { prompt ->
            assertFalse("anna@example.com" in prompt, "e-mail leaked into an LLM prompt")
        }
        assertTrue(llm.prompts.any { "lower back pain" in it }, "injury text must still reach the coach")
        assertTrue(llm.prompts.any { "\"age\":34" in it }, "age must still reach the coach")
    }

    @Test
    fun `chat prompts never contain identifiers typed into the conversation`() = runBlocking {
        val llm = CapturingLlmClient()
        val pipeline = pipeline()
        val catalog = BuiltInExerciseCatalog()
        val resolver = DefaultTrainingPlanResolver(catalog)
        val chat = ChatService(
            paidLlmClient = llm,
            freeLlmClient = llm,
            llmRepairer = LlmRepairer(pipeline),
            aiService = aiService(llm),
            exerciseCatalog = catalog,
            trainingPlanResolver = resolver
        )

        chat.chat(
            AiChatRequest(
                profile = profile(),
                messages = listOf(
                    AiChatMessage("user", "I'm Oleg, my phone is +7 916 555-12-34"),
                    AiChatMessage("assistant", "Hi!"),
                    AiChatMessage("user", "email me the plan at oleg@corp.ru, my knee hurts")
                )
            )
        )

        assertTrue(llm.prompts.isNotEmpty())
        llm.prompts.forEach { prompt ->
            assertFalse("555-12-34" in prompt, "phone leaked into an LLM prompt")
            assertFalse("oleg@corp.ru" in prompt, "e-mail leaked into an LLM prompt")
        }
        assertTrue(llm.prompts.any { "my knee hurts" in it }, "the actual question must still reach the coach")
    }

    // ── Logs / telemetry ────────────────────────────────────────────────────────

    @Test
    fun `exhausted pipeline does not put raw model output into the exception by default`() {
        val secretEcho = "user said: my HIV status is positive"
        val error = assertThrows<LlmPipelineExhaustedException> {
            runBlocking {
                pipeline().run(
                    logger = LoggerFactory.getLogger("test"),
                    operation = "test",
                    requestId = "r1",
                    basePrompt = "p",
                    callModel = { secretEcho },
                    strategy = Int.serializer(),
                    validator = SchemaValidator { emptyList() }
                )
            }
        }
        assertFalse("HIV" in (error.message ?: ""), error.message)
    }

    @Test
    fun `raw store purges dumps older than the retention window`(@TempDir dir: Path) {
        val now = Instant.parse("2026-10-08T12:00:00Z")
        val stale = Files.writeString(dir.resolve("chat_old_attempt1_raw.txt"), "old")
        Files.setLastModifiedTime(stale, FileTime.from(now.minus(Duration.ofHours(30))))
        val fresh = Files.writeString(dir.resolve("chat_new_attempt1_raw.txt"), "new")
        Files.setLastModifiedTime(fresh, FileTime.from(now.minus(Duration.ofHours(1))))

        LlmRawStore(
            enabled = true,
            baseDir = dir,
            retention = Duration.ofHours(24),
            clock = Clock.fixed(now, ZoneOffset.UTC)
        )

        assertFalse(Files.exists(stale), "dump older than retention must be deleted")
        assertTrue(Files.exists(fresh), "dump inside retention must be kept")
    }

    @Test
    fun `disabled raw store never touches the directory`(@TempDir dir: Path) {
        val store = LlmRawStore(enabled = false, baseDir = dir.resolve("llm"))
        store.write("chat", "r1", 1, "raw", "content")
        assertFalse(Files.exists(dir.resolve("llm")))
    }

    // ── Payments ledger ─────────────────────────────────────────────────────────

    @Test
    fun `payments ledger keeps no card or payer details`() {
        val providerResponse = """
            {"id":"2d3f5a4b-000f-5000-9000-1b2c3d4e5f60","status":"succeeded","paid":true,
             "amount":{"value":"299.00","currency":"RUB"},
             "payment_method":{"type":"bank_card","card":{"first6":"555555","last4":"4444","expiry_month":"12","expiry_year":"2030","issuer_name":"Sberbank"}},
             "authorization_details":{"rrn":"123456789","auth_code":"654321"},
             "metadata":{"userId":"uid-1","periodDays":"30"}}
        """.trimIndent()
        val payment = json.decodeFromString(YooKassaPayment.serializer(), providerResponse)

        val ledger = payment.toLedgerPayload()

        listOf("last4", "4444", "first6", "issuer_name", "auth_code", "rrn").forEach {
            assertFalse(it in ledger, "ledger must not contain '$it': $ledger")
        }
        assertTrue("299.00" in ledger && "uid-1" in ledger && payment.id in ledger)
    }

    // ── helpers ─────────────────────────────────────────────────────────────────

    private class CapturingLlmClient : LLMClient {
        val prompts: MutableList<String> = Collections.synchronizedList(mutableListOf())
        override suspend fun generateJson(prompt: String, schema: ResponseSchema?): String {
            prompts += prompt
            return "{}"
        }
    }

    private fun pipeline() = LlmPipeline(
        config = PipelineConfig(),
        extractor = ResponseExtractor(),
        sanitizer = JsonSanitizer(),
        decoder = Decoder(json),
        feedback = FeedbackComposer(),
        rawStore = LlmRawStore(enabled = false),
        tracker = LlmErrorTracker()
    )

    private fun aiService(llm: LLMClient): AiService {
        val catalog = BuiltInExerciseCatalog()
        return AiService(
            paidLlmClient = llm,
            freeLlmClient = llm,
            llmRepairer = LlmRepairer(pipeline()),
            exerciseCatalog = catalog,
            trainingPlanResolver = DefaultTrainingPlanResolver(catalog)
        )
    }

    private fun profile() = AiProfile(
        age = 34,
        sex = "MALE",
        heightCm = 180,
        weightKg = 82.0,
        goal = "MUSCLE_GAIN",
        experienceLevel = 3,
        weeklySchedule = mapOf("Mon" to true, "Wed" to true, "Fri" to true),
        locale = "en-US"
    )
}
