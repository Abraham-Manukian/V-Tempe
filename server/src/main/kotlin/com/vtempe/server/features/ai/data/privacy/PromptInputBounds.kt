package com.vtempe.server.features.ai.data.privacy

import com.vtempe.server.features.ai.api.AiRequestLimits as L
import com.vtempe.server.shared.dto.chat.AiChatRequest
import com.vtempe.server.shared.dto.profile.AiProfile
import java.time.LocalDate
import java.time.ZoneOffset

/**
 * Cuts every caller-controlled part of an AI request down to what a prompt can use (VT-AUD-010).
 * Applied BEFORE [DirectIdentifierRedactor] and prompt building, so neither ever works on more
 * text than the limits in [L] allow. Lengths count UTF-16 chars; a cut never splits a surrogate pair.
 */
internal fun AiProfile.boundedForPrompt(today: LocalDate = LocalDate.now(ZoneOffset.UTC)): AiProfile = copy(
    sex = sex.cut(SHORT),
    goal = goal.cut(SHORT),
    equipment = equipment.boundedList(),
    dietaryPreferences = dietaryPreferences.boundedList(),
    allergies = allergies.boundedList(),
    injuries = injuries.boundedList(),
    healthNotes = healthNotes.boundedList(),
    weeklySchedule = weeklySchedule.filterKeys { it in DAY_KEYS },
    lifestyleActivity = lifestyleActivity.cut(SHORT),
    locale = locale?.cut(SHORT),
    trainingMode = trainingMode.cut(SHORT),
    coachTrainerId = coachTrainerId.cut(SHORT),
    llmMode = llmMode?.cut(SHORT),
    recentWorkouts = recentWorkouts.take(L.MAX_RECENT_WORKOUTS).map { w ->
        w.copy(
            date = w.date.cut(SHORT),
            notes = w.notes.cut(L.MAX_NOTE_CHARS),
            exercises = w.exercises.take(L.MAX_FREE_TEXT_ITEMS).map { it.copy(exerciseId = it.exerciseId.cut(SHORT)) },
        )
    },
    sleepHistory = SleepHistoryPolicy.recent(sleepHistory, today).take(L.MAX_SLEEP_ENTRIES).map { it.copy(notes = it.notes.cut(L.MAX_NOTE_CHARS)) },
    recentWeights = recentWeights.take(L.MAX_WEIGHT_ENTRIES).map { it.copy(date = it.date.cut(SHORT)) },
    trainingFocus = trainingFocus.cut(SHORT),
    splitPreference = splitPreference.cut(SHORT),
)

/** Keeps the latest message intact (its size is validated at the route) plus the last
 *  [L.MAX_HISTORY_MESSAGES] earlier ones, each cut to [L.MAX_HISTORY_MESSAGE_CHARS]. */
internal fun AiChatRequest.boundedForPrompt(): AiChatRequest {
    val recent = messages.takeLast(L.MAX_HISTORY_MESSAGES + 1)
    return copy(
        profile = profile.boundedForPrompt(),
        locale = locale?.cut(SHORT),
        messages = recent.mapIndexed { i, m ->
            val isLatest = i == recent.lastIndex
            m.copy(
                role = m.role.cut(SHORT),
                content = if (isLatest) m.content.cut(L.MAX_CHAT_MESSAGE_CHARS) else m.content.cutWithEllipsis(L.MAX_HISTORY_MESSAGE_CHARS),
            )
        },
    )
}

/** The message the coach answers: the last one written by the user (or the last at all). */
internal fun AiChatRequest.latestUserMessage(): String =
    (messages.lastOrNull { it.role.equals("user", ignoreCase = true) } ?: messages.lastOrNull())?.content.orEmpty()

private const val SHORT = 64
private val DAY_KEYS = setOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")

private fun List<String>.boundedList(): List<String> = take(L.MAX_FREE_TEXT_ITEMS).map { it.cut(L.MAX_FREE_TEXT_CHARS) }

private fun String.cut(max: Int): String {
    if (length <= max) return this
    val end = if (this[max - 1].isHighSurrogate()) max - 1 else max
    return substring(0, end)
}

private fun String.cutWithEllipsis(max: Int): String = if (length <= max) this else cut(max) + "…"
