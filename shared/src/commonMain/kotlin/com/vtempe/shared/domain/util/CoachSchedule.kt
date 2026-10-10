package com.vtempe.shared.domain.util

import kotlinx.datetime.Clock
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.datetime.todayIn

/**
 * Rolling 7-day plan schedule.
 *
 * Week 0  = days 0-6  after epoch (first bootstrap date)
 * Week 1  = days 7-13, etc.
 *
 * Plans are generated once per week and cached in the DB.
 * Two days before the current week ends the app silently pre-fetches
 * the next week's plan in the background.
 */
object CoachSchedule {

    /** Start pre-fetching the next week when this many days remain in the current week. */
    const val PREFETCH_DAYS_BEFORE_EXPIRY = 2

    /**
     * Returns the weekIndex that is active today.
     * If epoch is not set yet (first ever run), returns 0.
     */
    fun currentWeekIndex(epochDateMs: Long?): Int {
        epochDateMs ?: return 0
        val epoch = Instant.fromEpochMilliseconds(epochDateMs)
            .toLocalDateTime(TimeZone.currentSystemDefault()).date
        val today = Clock.System.todayIn(TimeZone.currentSystemDefault())
        val elapsed = today.toEpochDays() - epoch.toEpochDays()
        return maxOf(0, elapsed.toInt() / 7)
    }

    /**
     * First calendar day of plan week [weekIndex]: the epoch (Monday of the first bootstrap week)
     * plus 7 days per week, or this week's Monday before the epoch exists. Sent to the server so
     * a plan is dated in ITS week instead of "server's current week + index" (VT-AUD-012).
     */
    fun weekStart(epochDateMs: Long?, weekIndex: Int, today: LocalDate = Clock.System.todayIn(TimeZone.currentSystemDefault())): LocalDate {
        val base = epochDateMs?.let {
            Instant.fromEpochMilliseconds(it).toLocalDateTime(TimeZone.currentSystemDefault()).date
        } ?: today.minus(DatePeriod(days = today.dayOfWeek.ordinal))
        return base.plus(DatePeriod(days = 7 * weekIndex))
    }

    /**
     * How many days remain until the current week rolls over (1..7).
     * Returns 7 when epoch is not set (= week just started).
     */
    fun daysUntilWeekEnd(epochDateMs: Long?): Int {
        epochDateMs ?: return 7
        val epoch = Instant.fromEpochMilliseconds(epochDateMs)
            .toLocalDateTime(TimeZone.currentSystemDefault()).date
        val today = Clock.System.todayIn(TimeZone.currentSystemDefault())
        val elapsed = today.toEpochDays() - epoch.toEpochDays()
        val dayInWeek = elapsed.toInt() % 7
        return 7 - dayInWeek   // always 1..7
    }
}
