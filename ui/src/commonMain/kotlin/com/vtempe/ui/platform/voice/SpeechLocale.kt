package com.vtempe.ui.platform.voice

/**
 * Picks the recognizer locale for the app language [requested]. The app stores either a bare
 * language ("ru", from Settings) or a full tag ("ru-RU", from onboarding), while recognizers list
 * regional locales. An exact match among [candidates] wins, then the first candidate of the same
 * language (callers order [candidates] by preference), else [requested] as given.
 * Tags compare case-insensitively, with `_` and `-` treated alike.
 */
fun resolveSpeechLocale(requested: String?, candidates: List<String>): String? {
    val wanted = requested?.trim()?.takeIf { it.isNotEmpty() } ?: return null
    return candidates.firstOrNull { normalized(it) == normalized(wanted) }
        ?: candidates.firstOrNull { language(it) == language(wanted) }
        ?: wanted
}

private fun normalized(tag: String) = tag.trim().replace('_', '-').lowercase()

private fun language(tag: String) = normalized(tag).substringBefore('-')
