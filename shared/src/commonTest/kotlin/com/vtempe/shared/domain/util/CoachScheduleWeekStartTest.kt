package com.vtempe.shared.domain.util

import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlin.test.Test
import kotlin.test.assertEquals

/** VT-AUD-012: the week start the client sends with every plan request. */
class CoachScheduleWeekStartTest {
    @Test
    fun weekStartCountsFromTheEpochMonday() {
        val epoch = LocalDate(2025, 10, 6).atStartOfDayIn(TimeZone.currentSystemDefault()).toEpochMilliseconds()
        assertEquals(LocalDate(2025, 10, 6), CoachSchedule.weekStart(epoch, 0))
        assertEquals(LocalDate(2026, 10, 5), CoachSchedule.weekStart(epoch, 52))
        assertEquals(LocalDate(2026, 10, 12), CoachSchedule.weekStart(epoch, 53))
    }

    @Test
    fun withoutAnEpochTheCurrentMondayIsUsed() {
        assertEquals(LocalDate(2026, 10, 5), CoachSchedule.weekStart(null, 0, today = LocalDate(2026, 10, 10)))
        assertEquals(LocalDate(2026, 10, 12), CoachSchedule.weekStart(null, 1, today = LocalDate(2026, 10, 10)))
    }
}
