package com.vtempe.shared.domain.exercise

import kotlinx.serialization.Serializable

@Serializable
data class LocalizedText(
    val en: String,
    val ru: String
) {
    fun resolve(localeTag: String?): String =
        if (localeTag?.lowercase()?.startsWith("ru") == true) ru else en
}
