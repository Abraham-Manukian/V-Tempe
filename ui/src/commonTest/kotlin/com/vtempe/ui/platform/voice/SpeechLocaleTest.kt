package com.vtempe.ui.platform.voice

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SpeechLocaleTest {

    @Test
    fun aBareLanguageFromSettingsGetsTheRecognizersRegionalLocale() {
        assertEquals("ru_RU", resolveSpeechLocale("ru", listOf("en_US", "ru_RU", "ru_UA")))
    }

    @Test
    fun anExactTagWinsOverAnEarlierSameLanguageCandidate() {
        assertEquals("ru_UA", resolveSpeechLocale("ru-UA", listOf("ru_RU", "ru_UA")))
    }

    @Test
    fun tagsCompareIgnoringCaseAndSeparator() {
        assertEquals("en-US", resolveSpeechLocale("EN_us", listOf("en-US")))
    }

    @Test
    fun anUnknownLanguageIsPassedThroughForThePlatformToReport() {
        assertEquals("de", resolveSpeechLocale("de", listOf("en_US", "ru_RU")))
    }

    @Test
    fun noAppLanguageLeavesTheSystemDefault() {
        assertNull(resolveSpeechLocale(null, listOf("en_US")))
        assertNull(resolveSpeechLocale("  ", listOf("en_US")))
    }
}
