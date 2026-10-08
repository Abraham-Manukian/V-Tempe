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

    private val EMAIL = Regex("[A-Za-z0-9._%+-]+@[A-Za-z0-9-]+(?:\\.[A-Za-z0-9-]+)*\\.[A-Za-z]{2,}")

    // "+<country><9..14 more digits>" with common separators, e.g. "+7 (900) 123-45-67", "+44 20 7946 0958".
    private val INTERNATIONAL_PHONE = Regex("(?<![\\w+])\\+\\d(?:[\\s().-]*\\d){9,14}(?!\\d)")

    // Russian domestic format: 8 + 10 digits, e.g. "89001234567", "8 (900) 123-45-67".
    private val RU_DOMESTIC_PHONE = Regex("(?<![\\w+])8(?:[\\s().-]*\\d){10}(?!\\d)")

    // Telegram/Instagram-style handles. Runs after EMAIL, so "user@mail.ru" is already gone.
    private val HANDLE = Regex("(?<![\\w.@])@[A-Za-z][A-Za-z0-9_]{3,31}\\b")

    fun redact(text: String): String {
        if (text.isEmpty()) return text
        return text
            .replace(EMAIL, EMAIL_PLACEHOLDER)
            .replace(INTERNATIONAL_PHONE, PHONE_PLACEHOLDER)
            .replace(RU_DOMESTIC_PHONE, PHONE_PLACEHOLDER)
            .replace(HANDLE, HANDLE_PLACEHOLDER)
    }
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
