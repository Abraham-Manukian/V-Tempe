package com.vtempe.server

import com.vtempe.server.features.ai.data.catalog.BuiltInExerciseCatalog
import com.vtempe.server.features.ai.data.privacy.boundedForPrompt
import com.vtempe.server.features.ai.data.resolver.DefaultTrainingPlanResolver
import com.vtempe.server.features.ai.data.service.buildBundlePrompt
import com.vtempe.server.features.ai.data.service.normalizeAdvice
import com.vtempe.server.features.ai.data.service.validateSleepAdvice
import com.vtempe.server.shared.dto.advice.AiAdviceResponse
import com.vtempe.server.shared.dto.bootstrap.AiBootstrapRequest
import com.vtempe.server.shared.dto.profile.AiProfile
import com.vtempe.server.shared.dto.profile.AiSleepEntry
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.util.Locale

/** VT-AUD-038 (blank sleep tips), VT-AUD-039 (invalid nights), VT-AUD-040 (old nights as "recent"). */
class SleepInputsTest {

    private val today = LocalDate.of(2026, 10, 10)
    private val profile = AiProfile(
        age = 30, sex = "MALE", heightCm = 180, weightKg = 80.0, goal = "MAINTAIN", experienceLevel = 3,
        equipment = emptyList(), weeklySchedule = mapOf("Mon" to true),
    )

    @Test
    fun `blank tips are dropped and the answer fails validation`() {
        val blank = normalizeAdvice(AiAdviceResponse(messages = listOf("   ", "\t\n"), disclaimer = " "))
        assertEquals(emptyList<String>(), blank.messages)
        assertNull(blank.disclaimer)
        assertNotNull(validateSleepAdvice(blank))

        assertNotNull(validateSleepAdvice(normalizeAdvice(AiAdviceResponse(messages = listOf("...", "—")))))

        val mixed = normalizeAdvice(AiAdviceResponse(messages = listOf(" ", "Keep a fixed wake time.", "Keep a fixed wake time.")))
        assertEquals(listOf("Keep a fixed wake time."), mixed.messages)
        assertNull(validateSleepAdvice(mixed))
    }

    @Test
    fun `only valid nights of the last two weeks reach the prompt`() {
        val history = listOf(
            AiSleepEntry("2026-10-09", 300),
            AiSleepEntry("2026-10-09", 900), // duplicate date — newest-first order keeps the first
            AiSleepEntry("2000-01-01", 60),
            AiSleepEntry("not-a-date", 400),
            AiSleepEntry("2026-10-08", -120),
            AiSleepEntry("2026-10-07", 2_000),
            AiSleepEntry("2026-12-01", 420),
            AiSleepEntry(" 2026-09-27 ", 480),
            AiSleepEntry("2026-09-25", 480), // 15 days ago
            AiSleepEntry("2026-10-11", 0), // client ahead of UTC
        )
        val kept = profile.copy(sleepHistory = history).boundedForPrompt(today).sleepHistory
        assertEquals(listOf("2026-10-11" to 0, "2026-10-09" to 300, "2026-09-27" to 480), kept.map { it.date to it.durationMinutes })
    }

    @Test
    fun `an ancient short night no longer triggers a recovery deficit, a recent one still does`() {
        fun prompt(entries: List<AiSleepEntry>) = buildBundlePrompt(
            Json, Locale.ENGLISH, "metric", "kilograms",
            AiBootstrapRequest(profile.copy(sleepHistory = entries).boundedForPrompt(today), 0, "en"),
            BuiltInExerciseCatalog(), DefaultTrainingPlanResolver(BuiltInExerciseCatalog()),
        )
        val ancient = prompt(listOf(AiSleepEntry("2000-01-01", 60)))
        assertFalse("2000-01-01" in ancient)
        assertFalse("RECOVERY DEFICIT" in ancient)
        assertFalse("RECOVERY DEFICIT" in prompt(listOf(AiSleepEntry("2026-10-09", -120))))
        assertTrue("RECOVERY DEFICIT" in prompt(listOf(AiSleepEntry("2026-10-09", 300))))
    }
}
