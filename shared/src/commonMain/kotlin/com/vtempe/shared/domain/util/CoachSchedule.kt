package com.vtempe.shared.domain.util

import kotlinx.datetime.Clock
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
