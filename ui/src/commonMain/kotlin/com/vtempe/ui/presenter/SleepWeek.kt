package com.vtempe.ui.presenter

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import kotlinx.datetime.Clock
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.datetime.todayIn
import kotlin.math.roundToInt

/**
 * Minutes logged for each day Mon..Sun of the week containing [today]; null where nothing was
 * logged (VT-AUD-045/046) — a missing night is not a night of zero sleep.
 */
internal fun sleepWeekMinutes(today: LocalDate, minutesFor: (LocalDate) -> Int?): List<Int?> {
    val monday = today.minus(DatePeriod(days = today.dayOfWeek.ordinal))
    return (0..6).map { offset -> minutesFor(monday.plus(DatePeriod(days = offset))) }
}

/** Average over the nights actually logged, or null when none were. */
internal fun List<Int?>.averageLoggedMinutes(): Int? =
    filterNotNull().takeIf { it.isNotEmpty() }?.average()?.roundToInt()

/** "7:30" for 450 minutes; "—" for a day without an entry. */
internal fun formatSleepMinutes(minutes: Int?): String {
    if (minutes == null) return "—"
    return "${minutes / 60}:${(minutes % 60).toString().padStart(2, '0')}"
}

/**
 * Today's local date, re-emitted when it changes — at midnight or after a time-zone change —
 * so the diary follows the calendar while the screen stays open (VT-AUD-044).
 */
internal fun localDates(
    today: () -> LocalDate = { Clock.System.todayIn(TimeZone.currentSystemDefault()) },
    checkEveryMs: Long = 60_000,
): Flow<LocalDate> = flow {
    while (true) {
        emit(today())
        delay(checkEveryMs)
    }
}.distinctUntilChanged()
