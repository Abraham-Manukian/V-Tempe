package com.vtempe.shared.domain.exercise

import kotlinx.serialization.Serializable

@Serializable
data class ExerciseTechnique(
    val summary: LocalizedText,
    val focusEn: List<String>,
    val focusRu: List<String>,
    val keyCue: LocalizedText,
    val stepsEn: List<String>,
    val stepsRu: List<String>,
    val defaultRestSeconds: Int
) {
    fun focus(localeTag: String?): List<String> =
        if (localeTag?.lowercase()?.startsWith("ru") == true) focusRu else focusEn

    fun steps(localeTag: String?): List<String> =
        if (localeTag?.lowercase()?.startsWith("ru") == true) stepsRu else stepsEn
}
