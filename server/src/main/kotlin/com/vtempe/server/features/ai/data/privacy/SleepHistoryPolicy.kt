package com.vtempe.server.features.ai.data.privacy

import com.vtempe.server.shared.dto.profile.AiSleepEntry
import java.time.LocalDate

/**
 * Which logged nights may describe the user's CURRENT recovery (VT-AUD-039, VT-AUD-040). The
 * client's list is not trusted to be valid or recent: a night needs an ISO date, 0..24 h, and a
 * date within the last [RECENT_DAYS] days. [today] is the server's UTC date; one day of slack on
 * each side covers every client time zone. Same limits as the app's `SleepBounds`.
 */
internal object SleepHistoryPolicy {
    const val RECENT_DAYS = 14L
    val MINUTES = 0..24 * 60

    /** Valid, recent nights — one per date, newest first. */
    fun recent(entries: List<AiSleepEntry>, today: LocalDate): List<AiSleepEntry> {
        val earliest = today.minusDays(RECENT_DAYS)
        val latest = today.plusDays(1)
        return entries.mapNotNull { entry ->
            val date = runCatching { LocalDate.parse(entry.date.trim()) }.getOrNull() ?: return@mapNotNull null
            if (entry.durationMinutes !in MINUTES || date.isBefore(earliest) || date.isAfter(latest)) return@mapNotNull null
            date to entry.copy(date = date.toString())
        }
            .sortedByDescending { it.first }
            .distinctBy { it.first }
            .map { it.second }
    }
}
