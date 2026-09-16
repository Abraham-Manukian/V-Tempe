package com.vtempe.ui.screens

import kotlinx.datetime.Clock
import com.vtempe.ui.util.toShortKey
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

private val weekOrder = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")

internal fun currentWeekdayKey(): String {
    val day = Clock.System.now()
        .toLocalDateTime(TimeZone.currentSystemDefault())
        .date
        .dayOfWeek
    return day.toShortKey()
}

internal fun resolveNutritionSelectedDay(
    selectedDay: String,
    availableDays: Set<String>
): String {
    if (selectedDay in availableDays) return selectedDay

    val today = currentWeekdayKey()
    if (today in availableDays) return today

    return weekOrder.firstOrNull { it in availableDays } ?: selectedDay
}
