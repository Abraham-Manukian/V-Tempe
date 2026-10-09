package com.vtempe.server.features.ai.data.privacy

import com.vtempe.server.shared.dto.chat.AiChatRequest
import com.vtempe.server.shared.dto.profile.AiProfile

/**
 * Strips direct contact identifiers (e-mail addresses, phone numbers, messenger @handles) out of
 * the FREE-TEXT parts of an AI request before it is put into a prompt for the external LLM
 * provider. The model never needs them to build a plan, but users do type them into fields like
 * "injuries" or into the chat ("call me at +7 ..."), and every prompt is a cross-border transfer.
 *
 * Deliberately narrow: structured health/body data (age, sex, height, weight, injuries text,
 * allergies) is what the coach legitimately needs and is left untouched — only substrings that
 * match an identifier pattern are replaced with a neutral placeholder. Patterns are conservative
 * (a phone needs a `+` country prefix or the Russian `8XXXXXXXXXX` shape with 11 digits) so that
 * training numbers like "3x10", "120/80" or ISO dates are never mangled.
 */
object DirectIdentifierRedactor {
    const val EMAIL_PLACEHOLDER = "[email]"
    const val PHONE_PLACEHOLDER = "[phone]"
    const val HANDLE_PLACEHOLDER = "[handle]"

    // "+<country><9..14 more digits>" with common separators, e.g. "+7 (900) 123-45-67", "+44 20 7946 0958".
    private val INTERNATIONAL_PHONE = Regex("(?<![\\w+])\\+\\d(?:[\\s().-]*\\d){9,14}(?!\\d)")

    // Russian domestic format: 8 + 10 digits, e.g. "89001234567", "8 (900) 123-45-67".
    private val RU_DOMESTIC_PHONE = Regex("(?<![\\w+])8(?:[\\s().-]*\\d){10}(?!\\d)")

    // Telegram/Instagram-style handles. Runs after EMAIL, so "user@mail.ru" is already gone.
    private val HANDLE = Regex("(?<![\\w.@])@[A-Za-z][A-Za-z0-9_]{3,31}\\b")

    fun redact(text: String): String {
        if (text.isEmpty()) return text
        return redactEmails(text)
            .replace(INTERNATIONAL_PHONE, PHONE_PLACEHOLDER)
            .replace(RU_DOMESTIC_PHONE, PHONE_PLACEHOLDER)
            .replace(HANDLE, HANDLE_PLACEHOLDER)
    }

    /**
     * Replaces e-mail addresses in ONE linear pass (VT-AUD-011).
     *
     * Same matches as the former `[A-Za-z0-9._%+-]+@[A-Za-z0-9-]+(?:\.[A-Za-z0-9-]+)*\.[A-Za-z]{2,}`
     * regex, but that pattern, unanchored, re-scanned the rest of a long letter run from every
     * starting position before failing on the missing `@` — quadratic time on input like 20 000
     * letters. Here only an `@` starts work: the local part is the run of local-part characters
     * right before it and the domain the run right after it; runs between two `@` are disjoint,
     * so every character is visited a bounded number of times.
     */
    private fun redactEmails(text: String): String {
        var at = text.indexOf('@')
        if (at < 0) return text
        val out = StringBuilder(text.length)
        var copiedUpTo = 0 // text before this index is already in [out]
        while (at >= 0) {
            var start = at
            while (start > copiedUpTo && isLocalPartChar(text[start - 1])) start--
            val end = if (start < at) emailDomainEnd(text, at + 1) else -1
            if (end > 0) {
                out.append(text, copiedUpTo, start).append(EMAIL_PLACEHOLDER)
                copiedUpTo = end
                at = text.indexOf('@', end)
            } else {
                at = text.indexOf('@', at + 1)
            }
        }
        return out.append(text, copiedUpTo, text.length).toString()
    }

    /**
     * End (exclusive) of the domain starting at [from], or -1. Mirrors the regex's greedy
     * backtracking: labels `[A-Za-z0-9-]+` joined by single dots, at least two of them, and the
     * last one cut to its leading ASCII letters, which must number two or more; the most labels
     * that satisfy this win.
     */
    private fun emailDomainEnd(text: String, from: Int): Int {
        val labelStarts = ArrayList<Int>()
        var i = from
        while (i < text.length && isLabelChar(text[i])) {
            labelStarts += i
            while (i < text.length && isLabelChar(text[i])) i++
            if (i + 1 < text.length && text[i] == '.' && isLabelChar(text[i + 1])) i++ else break
        }
        for (k in labelStarts.indices.reversed()) {
            if (k == 0) break
            var letters = labelStarts[k]
            while (letters < text.length && text[letters].isAsciiLetter()) letters++
            if (letters - labelStarts[k] >= 2) return letters
        }
        return -1
    }

    private fun isLocalPartChar(c: Char) = c.isAsciiLetterOrDigit() || c == '.' || c == '_' || c == '%' || c == '+' || c == '-'
    private fun isLabelChar(c: Char) = c.isAsciiLetterOrDigit() || c == '-'
    private fun Char.isAsciiLetter() = this in 'a'..'z' || this in 'A'..'Z'
    private fun Char.isAsciiLetterOrDigit() = isAsciiLetter() || this in '0'..'9'
}

/** Same profile with every user-typed free-text field passed through [DirectIdentifierRedactor]. */
fun AiProfile.withoutDirectIdentifiers(): AiProfile {
    val redact = DirectIdentifierRedactor::redact
    return copy(
        equipment = equipment.map(redact),
        dietaryPreferences = dietaryPreferences.map(redact),
        allergies = allergies.map(redact),
        injuries = injuries.map(redact),
        healthNotes = healthNotes.map(redact),
        recentWorkouts = recentWorkouts.map { it.copy(notes = redact(it.notes)) },
        sleepHistory = sleepHistory.map { it.copy(notes = redact(it.notes)) },
    )
}

/** Chat request with the profile and every message body stripped of direct identifiers. */
fun AiChatRequest.withoutDirectIdentifiers(): AiChatRequest = copy(
    profile = profile.withoutDirectIdentifiers(),
    messages = messages.map { it.copy(content = DirectIdentifierRedactor.redact(it.content)) },
)
