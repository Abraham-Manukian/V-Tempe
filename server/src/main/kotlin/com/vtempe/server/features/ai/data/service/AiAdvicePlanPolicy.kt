package com.vtempe.server.features.ai.data.service

import com.vtempe.server.shared.dto.advice.AiAdviceResponse

internal fun normalizeAdvice(advice: AiAdviceResponse): AiAdviceResponse {
    val normalizedMessages = advice.messages.map(::sanitizeText).filter { it.isNotEmpty() }
    val normalizedDisclaimer = advice.disclaimer?.let(::sanitizeText)?.takeIf { it.isNotEmpty() }
    return advice.copy(
        messages = if (normalizedMessages.isEmpty()) advice.messages else normalizedMessages,
        disclaimer = normalizedDisclaimer ?: advice.disclaimer
    )
}

internal fun validateSleepAdvice(advice: AiAdviceResponse): String? {
    if (advice.messages.isEmpty()) return "messages must contain at least one tip"
    return null
}
