package com.vtempe.server

import com.vtempe.server.features.ai.data.llm.LLMClient
import com.vtempe.server.features.ai.data.llm.schema.ResponseSchema
import com.vtempe.server.features.ai.data.service.LlmClientRouter
import com.vtempe.server.shared.dto.profile.AiProfile
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.slf4j.LoggerFactory

/** VT-AUD-004 defence in depth: the router itself only spends paid tokens on an explicit "paid"
 *  (which the AI routes set solely for a server-granted tier) — never on null or junk values. */
class LlmClientRouterTest {

    private class CountingClient(private val reply: String) : LLMClient {
        var calls = 0
        override suspend fun generateJson(prompt: String, schema: ResponseSchema?): String {
            calls++
            return reply
        }
    }

    @Test
    fun `paid client is used only for an explicit paid mode`() = runBlocking {
        val paid = CountingClient("paid")
        val free = CountingClient("free")
        val router = LlmClientRouter(paid, free)
        val logger = LoggerFactory.getLogger(LlmClientRouterTest::class.java)

        val replies = listOf(null, "", "free", "FREE", "premium", "paid", " Paid ").map { mode ->
            router.generateWithFallback(logger, profile(mode), "prompt", "test", "req")
        }

        assertEquals(listOf("free", "free", "free", "free", "free", "paid", "paid"), replies)
        assertEquals(2, paid.calls)
        assertEquals(5, free.calls)
    }

    private fun profile(llmMode: String?) = AiProfile(
        age = 30, sex = "MALE", heightCm = 180, weightKg = 80.0, goal = "MAINTAIN",
        experienceLevel = 2, llmMode = llmMode
    )
}
