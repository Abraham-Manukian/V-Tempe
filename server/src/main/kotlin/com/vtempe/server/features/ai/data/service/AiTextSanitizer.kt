package com.vtempe.server.features.ai.data.service

import java.nio.ByteBuffer
import java.nio.CharBuffer
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction
import java.util.Locale

private val cp1251Charset: Charset = Charset.forName("windows-1251")
private val cp866Charset: Charset = Charset.forName("cp866")
private val latin1Charset: Charset = Charsets.ISO_8859_1
private val utf8Charset: Charset = Charsets.UTF_8
private val cyrillicRange = '\u0400'..'\u04FF'
private val whitespaceRegex = Regex("\\s+")
private val utf8MojibakeRegex = Regex("(?:Р.|С.|Ð.|Ñ.){2,}")

internal fun sanitizeText(raw: String): String {
    val trimmed = raw.trim()
    if (trimmed.isEmpty()) return ""

    var repaired = trimmed
    repeat(3) {
        val next = repairMojibake(repaired)
        if (next == repaired) return@repeat
        repaired = next
    }
    return whitespaceRegex.replace(repaired, " ").trim()
}

private fun repairMojibake(raw: String): String {
    if (!looksLikeMojibake(raw)) return raw

    val candidates = linkedSetOf(raw)
    transcode(raw, cp1251Charset, utf8Charset)?.let(candidates::add)
    transcode(raw, cp866Charset, utf8Charset)?.let(candidates::add)
    transcode(raw, cp866Charset, cp1251Charset)?.let(candidates::add)
    transcode(raw, latin1Charset, utf8Charset)?.let(candidates::add)

    val best = candidates.maxByOrNull(::readabilityScore) ?: raw
    return if (readabilityScore(best) > readabilityScore(raw) + 2) best else raw
}

private fun transcode(raw: String, source: Charset, target: Charset): String? {
    return runCatching {
        val encoder = source.newEncoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
        val decoder = target.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)

        val encoded = encoder.encode(CharBuffer.wrap(raw))
        val bytes = ByteArray(encoded.remaining())
        encoded.get(bytes)
        decoder.decode(ByteBuffer.wrap(bytes)).toString()
    }.getOrNull()?.takeIf { it.isNotBlank() && it != raw }
}

private fun readabilityScore(text: String): Int {
    val cyrillicCount = text.count { it in cyrillicRange }
    val letterCount = text.count { it.isLetter() }
    val whitespaceCount = text.count { it.isWhitespace() }
    val garbledCount = text.count { ch ->
        ch.code in 0x2500..0x257F ||
            ch.code in 0x2580..0x259F ||
            ch.code in 0x00C0..0x00FF
    }
    val replacementCount = text.count { it == '\uFFFD' }
    val mojibakeHits = utf8MojibakeRegex.findAll(text).count()
    return (cyrillicCount * 6) + letterCount + whitespaceCount -
        (garbledCount * 8) - (replacementCount * 12) - (mojibakeHits * 15)
}

private fun looksLikeMojibake(raw: String): Boolean =
    raw.any { ch ->
        ch.code in 0x2500..0x257F ||
            ch.code in 0x2580..0x259F ||
            ch.code in 0x00C0..0x00FF
    } || raw.contains('\uFFFD') || utf8MojibakeRegex.containsMatchIn(raw)

internal fun normalizeExerciseToken(raw: String): String =
    sanitizeText(raw)
        .lowercase(Locale.US)
        .replace('-', '_')
        .replace(' ', '_')
        .replace(Regex("_+"), "_")
        .trim('_')

internal fun hasCyrillic(text: String): Boolean = text.any { it in cyrillicRange }
