package com.vtempe.server

import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import com.vtempe.server.features.ai.data.llm.decode.Decoder
import com.vtempe.server.features.ai.data.llm.decode.SchemaValidator
import com.vtempe.server.features.ai.data.llm.extract.ResponseExtractor
import com.vtempe.server.features.ai.data.llm.feedback.FeedbackComposer
import com.vtempe.server.features.ai.data.llm.pipeline.LlmPipeline
import com.vtempe.server.features.ai.data.llm.pipeline.LlmPipelineExhaustedException
import com.vtempe.server.features.ai.data.llm.pipeline.PipelineConfig
import com.vtempe.server.features.ai.data.llm.repair.JsonSanitizer
import com.vtempe.server.features.ai.data.llm.telemetry.LlmErrorTracker
import com.vtempe.server.features.ai.data.llm.telemetry.LlmRawStore
import com.vtempe.server.features.ai.data.service.AiQualityMetrics
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.slf4j.LoggerFactory

/** VT-AUD-001: model output never reaches the logs, whatever the failure stage. */
class LogPrivacyTest {

    @Serializable
    enum class Kind { STRENGTH, CARDIO }

    @Serializable
    data class Sample(val reps: Int, val kind: Kind = Kind.STRENGTH, val note: String = "")

    private val appender = ListAppender<ILoggingEvent>()
    private val root = LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME) as Logger

    @BeforeEach
    fun attach() {
        appender.start()
        root.addAppender(appender)
    }

    @AfterEach
    fun detach() {
        root.detachAppender(appender)
    }

    private fun pipeline() = LlmPipeline(
        PipelineConfig(), // production defaults: no raw store, no snippets
        ResponseExtractor(), JsonSanitizer(), Decoder(Json { ignoreUnknownKeys = false }),
        FeedbackComposer(), LlmRawStore(enabled = false), LlmErrorTracker()
    )

    private fun run(raw: String, validator: SchemaValidator<Sample> = SchemaValidator { emptyList() }): Throwable? =
        runCatching {
            runBlocking {
                pipeline().run(
                    logger = LoggerFactory.getLogger("llm-test"),
                    operation = "privacy",
                    requestId = "r1",
                    basePrompt = "p",
                    callModel = { raw },
                    strategy = Sample.serializer(),
                    validator = validator,
                )
            }
        }.exceptionOrNull()

    private fun assertNoMarkerLogged() {
        assertTrue(appender.list.isNotEmpty(), "the failure must still be logged")
        appender.list.forEach { event ->
            val everything = listOfNotNull(
                event.formattedMessage,
                event.message,
                event.argumentArray?.joinToString(),
                event.throwableProxy?.message,
            ).joinToString(" ")
            assertFalse(MARKER in everything, "leaked: $everything")
        }
    }

    @Test
    fun `wrong type, unknown enum and broken syntax are logged without the model output`() {
        for (raw in listOf(
            """{"reps": "$MARKER"}""",
            """{"reps": 8, "kind": "$MARKER"}""",
            """{"reps": 8, "note": "$MARKER" "$MARKER" ::: [[[""",
            """{"reps": 8, "note": "$MARKER", }} trailing $MARKER""", // repairable: may succeed
        )) {
            appender.list.clear()
            val error = run(raw)
            if (error == null) {
                assertTrue(appender.list.none { MARKER in it.formattedMessage }, raw)
                continue
            }
            assertTrue(error is LlmPipelineExhaustedException, raw)
            assertFalse(MARKER in (error.message ?: ""))
            assertNoMarkerLogged()
            val warn = appender.list.first { it.formattedMessage.contains("stage=") }.formattedMessage
            assertTrue(warn.contains("operation") || warn.contains("privacy"), warn)
            assertTrue(warn.contains("attempt=") && warn.contains("stage="), warn)
        }
    }

    @Test
    fun `validation errors quoting the plan are logged as categories only`() {
        val error = run("""{"reps": 8}""", SchemaValidator { listOf("Mon meal[0].ingredients[1]: forbidden term 'peanut' in '$MARKER'") })
        assertTrue(error is LlmPipelineExhaustedException)
        assertNoMarkerLogged()
        assertTrue(appender.list.any { it.formattedMessage.contains("mon meal[#].ingredients[#]") })

        appender.list.clear()
        AiQualityMetrics.recordValidation(LoggerFactory.getLogger("q"), "op", "r", listOf("contains duplicate meals: $MARKER"))
        assertNoMarkerLogged()
    }

    private companion object {
        const val MARKER = "AUDIT_HEALTH_MARKER_hiv_positive"
    }
}
