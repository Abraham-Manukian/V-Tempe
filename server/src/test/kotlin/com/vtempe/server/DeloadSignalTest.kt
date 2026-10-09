package com.vtempe.server

import com.vtempe.server.features.ai.data.service.shouldForceDeload
import com.vtempe.server.shared.dto.profile.AiRecentWorkout
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** VT-AUD-016: the deload signal reads the two NEWEST workouts by date, whatever the order. */
class DeloadSignalTest {

    private fun w(date: String, completion: Double, rpe: Double? = null) =
        AiRecentWorkout(date = date, completionRate = completion, completedItems = 0, plannedItems = 1, totalVolumeKg = 0.0, averageRpe = rpe)

    private val newBad = listOf(w("2026-10-08", 0.2), w("2026-10-06", 0.2))
    private val oldGood = listOf(w("2026-09-29", 1.0), w("2026-09-27", 1.0))

    @Test
    fun `recent poor sessions trigger deload in any input order`() {
        val history = newBad + oldGood
        assertTrue(shouldForceDeload(history))            // newest-first, as the client sends it
        assertTrue(shouldForceDeload(history.reversed())) // oldest-first
        assertTrue(shouldForceDeload(listOf(oldGood[1], newBad[1], oldGood[0], newBad[0])))
    }

    @Test
    fun `old poor sessions do not override recent good ones`() {
        val history = listOf(w("2026-10-08", 1.0), w("2026-10-06", 0.95), w("2026-09-29", 0.2), w("2026-09-27", 0.2))
        assertFalse(shouldForceDeload(history))
        assertFalse(shouldForceDeload(history.reversed()))
    }

    @Test
    fun `high RPE on the newest sessions also triggers`() {
        assertTrue(shouldForceDeload(listOf(w("2026-10-08", 1.0, 9.0), w("2026-10-06", 1.0, 9.5), w("2026-09-01", 1.0, 6.0))))
    }

    @Test
    fun `short or undated history never triggers`() {
        assertFalse(shouldForceDeload(emptyList()))
        assertFalse(shouldForceDeload(listOf(w("2026-10-08", 0.1))))
        assertFalse(shouldForceDeload(listOf(w("not-a-date", 0.1), w("", 0.1), w("2026-10-08", 0.1))))
    }
}
