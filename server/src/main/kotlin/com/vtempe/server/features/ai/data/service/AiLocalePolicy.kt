package com.vtempe.server.features.ai.data.service

import java.util.Locale

internal fun measurementSystemLabel(locale: Locale): String =
    if (usesImperial(locale)) "imperial (pounds/inches)" else "metric (kilograms/centimeters)"

internal fun safeLocale(tag: String?): Locale {
    val candidate = tag?.let { runCatching { Locale.forLanguageTag(it) }.getOrNull() }
    return if (candidate == null || candidate.language.isNullOrBlank()) Locale.ENGLISH else candidate
}

private fun usesImperial(locale: Locale): Boolean =
    locale.country.equals("US", ignoreCase = true) ||
        locale.country.equals("LR", ignoreCase = true) ||
        locale.country.equals("MM", ignoreCase = true)
