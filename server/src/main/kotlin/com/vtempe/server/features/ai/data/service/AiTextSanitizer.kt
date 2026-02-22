package com.vtempe.server.features.ai.data.service

import java.nio.ByteBuffer
import java.nio.charset.Charset
import java.util.Locale

private val cp1251Charset: Charset = Charset.forName("windows-1251")
private val cyrillicRange = '\u0400'..'\u04FF'
private val whitespaceRegex = Regex("\\s+")

internal fun sanitizeText(raw: String): String {
    val trimmed = raw.trim()
    val decoded = decodeCp1251(trimmed) ?: trimmed
    return whitespaceRegex.replace(decoded, " ").trim()
}

private fun decodeCp1251(raw: String): String? {
    if (!looksLikeCp1251Garbage(raw)) return null
    val bytes = raw.map { (it.code and 0xFF).toByte() }.toByteArray()
    return runCatching {
        val decoded = cp1251Charset.decode(ByteBuffer.wrap(bytes)).toString()
        val cyrillicCount = decoded.count { it in cyrillicRange }
        if (cyrillicCount >= decoded.length / 3) decoded else null
    }.getOrNull()
}

private fun looksLikeCp1251Garbage(raw: String): Boolean =
    raw.any { ch ->
        ch.code in 0x2500..0x257F ||
            ch.code in 0x2580..0x259F ||
            ch.code in 0x00C0..0x00FF
    }

internal fun normalizeExerciseToken(raw: String): String =
    sanitizeText(raw)
        .lowercase(Locale.US)
        .replace('-', '_')
        .replace(' ', '_')
        .replace(Regex("_+"), "_")
        .trim('_')

internal fun hasCyrillic(text: String): Boolean = text.any { it in cyrillicRange }
