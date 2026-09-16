package com.vtempe.ui.util

import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals

class DayOfWeekExtTest {
    @Test
    fun calendarWeekMatchesNutritionKeysAndChartIndices() {
        val keys = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
        keys.forEachIndexed { index, key ->
            // 2026-09-07 is a Monday; exercise the same calendar input used by the UI.
            val day = LocalDate(2026, 9, 7 + index).dayOfWeek
            assertEquals(key, day.toShortKey())
            assertEquals(index, day.toWeekIndex())
        }
    }
}
