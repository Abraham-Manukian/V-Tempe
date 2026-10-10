package com.vtempe.shared.domain.model

import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus

/**
 * What a stored night of sleep may look like (VT-AUD-039, VT-AUD-040). The same rules guard local
 * logging, restored sync snapshots and what is sent to the coach; the server applies its own copy.
 */
object SleepBounds {
    /** A night is 0..24 h; 0 is an explicitly logged "no sleep", a missing day has no entry. */
    val MINUTES = 0..24 * 60

    /** Nights kept on the device. */
    const val HISTORY_DAYS = 14

    /** Only nights within this many days (today included) describe the user's current recovery. */
    const val RECENT_DAYS = 14

    fun parseDate(raw: String): LocalDate? = runCatching { LocalDate.parse(raw.trim()) }.getOrNull()

    fun isValid(date: String, minutes: Int): Boolean = parseDate(date) != null && minutes in MINUTES

    /** True when [date] is one of the last [RECENT_DAYS] days up to and including [today]. */
    fun isRecent(date: String, today: LocalDate): Boolean {
        val day = parseDate(date) ?: return false
        return day <= today && day > today.minus(DatePeriod(days = RECENT_DAYS))
    }
}
