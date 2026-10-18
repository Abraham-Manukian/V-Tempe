package com.vtempe.server.features.ai.domain

/**
 * Input budget of the AI endpoints (VT-AUD-010). Everything a caller sends is bounded BEFORE
 * any redaction, prompt building or LLM call, so the 30-per-minute rate limit can no longer be
 * outrun by making each request arbitrarily large.
 */
object AiInputLimits {
    /** Whole JSON body; the app's biggest request (chat with 30 messages + both plans) is far below. */
    const val MAX_BODY_BYTES = 512 * 1024

    /** The message the user just typed. The app enforces the same limit in its composer. */
    const val MAX_CHAT_MESSAGE_CHARS = 4_000

    /** Earlier chat turns the prompt keeps, and how much of each. */
    const val MAX_HISTORY_MESSAGES = 8
    const val MAX_HISTORY_MESSAGE_CHARS = 400

    /** User-typed profile lists (injuries, allergies, …): items and characters per item. */
    const val MAX_FREE_TEXT_ITEMS = 30
    const val MAX_FREE_TEXT_CHARS = 300
    const val MAX_NOTE_CHARS = 500

    /** History the client attaches to every request. */
    const val MAX_RECENT_WORKOUTS = 12
    const val MAX_SLEEP_ENTRIES = 31
    const val MAX_WEIGHT_ENTRIES = 60

    /** Final guard on any prompt sent to the provider. */
    const val MAX_PROMPT_CHARS = 120_000

    /** Supported body measurements — technical plausibility, not clinical eligibility. Same as
     *  the app's ProfileBounds (VT-AUD-035). */
    val AGE_YEARS = 10..110
    val HEIGHT_CM = 50..272
    val WEIGHT_KG = 20.0..400.0
}

/** The prompt built for a request is over [AiInputLimits.MAX_PROMPT_CHARS]; nothing was sent. */
class PromptTooLargeException : RuntimeException("prompt over the size budget")
