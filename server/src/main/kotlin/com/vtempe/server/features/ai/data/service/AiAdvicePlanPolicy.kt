package com.vtempe.server.features.ai.data.service

import com.vtempe.server.shared.dto.advice.AiAdviceResponse

/**
 * Cleans sleep advice. Blank tips are dropped and never restored (VT-AUD-038): an answer with no
 * real tip must reach [validateSleepAdvice] empty, so repair/fallback runs instead of the user
 * getting empty cards.
 */
internal fun normalizeAdvice(advice: AiAdviceResponse): AiAdviceResponse = advice.copy(
    messages = advice.messages.map(::sanitizeText).filter { it.isNotEmpty() }.distinct().take(MAX_SLEEP_TIPS),
    disclaimer = advice.disclaimer?.let(::sanitizeText)?.takeIf { it.isNotEmpty() },
)

/**
 * Tips are free text: they are checked to be present and readable, not as a verified schedule —
 * the response has no structured bedtime/duration fields to cross-check.
 */
internal fun validateSleepAdvice(advice: AiAdviceResponse): String? {
    if (advice.messages.isEmpty()) return "messages must contain at least one non-empty tip"
    val unreadable = advice.messages.indexOfFirst { tip -> tip.count(Char::isLetter) < MIN_TIP_LETTERS }
    if (unreadable >= 0) return "messages[$unreadable] is not a readable tip"
    return null
}

private const val MAX_SLEEP_TIPS = 10
private const val MIN_TIP_LETTERS = 3
