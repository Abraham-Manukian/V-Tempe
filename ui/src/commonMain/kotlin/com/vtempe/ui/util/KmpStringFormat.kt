package com.vtempe.ui.util

import kotlin.math.pow
import kotlin.math.round

/**
 * Минимальный форматтер для строк из Compose Resources на Kotlin/Native (iOS),
 * потому что `String.format(...)`/`java.util.Formatter` недоступны.
 *
 * Поддерживает:
 * - `%%` -> `%`
 * - `%d`, `%s`, `%f`
 * - позиционные: `%1$d`, `%2$s`, `%3$.1f` (точность для `f`)
 */
fun String.kmpFormat(vararg args: Any?): String = kmpFormatTemplate(this, *args)

fun kmpFormatTemplate(template: String, vararg args: Any?): String {
    if (args.isEmpty()) return template

    val out = StringBuilder(template.length + 16)
    var i = 0
    var sequentialIndex = 0

    while (i < template.length) {
        val ch = template[i]
        if (ch != '%') {
            out.append(ch)
            i++
            continue
        }

        if (i + 1 < template.length && template[i + 1] == '%') {
            out.append('%')
            i += 2
            continue
        }

        i++ // skip '%'

        // Optional positional index: \d+$
        var positionalIndex: Int? = null
        val startDigits = i
        while (i < template.length && template[i].isDigit()) i++
        if (i < template.length && i > startDigits && template[i] == '$') {
            positionalIndex = template.substring(startDigits, i).toIntOrNull()?.minus(1)
            i++ // skip '$'
        } else {
            // no positional index; rollback
            i = startDigits
        }

        // Optional precision: .\d+
        var precision: Int? = null
        if (i < template.length && template[i] == '.') {
            i++
            val startPrec = i
            while (i < template.length && template[i].isDigit()) i++
            precision = template.substring(startPrec, i).toIntOrNull()
        }

        if (i >= template.length) {
            out.append('%')
            break
        }

        val type = template[i]
        i++

        val argIndex = positionalIndex ?: sequentialIndex++
        val value = args.getOrNull(argIndex)

        when (type) {
            'd' -> out.append(
                when (value) {
                    is Number -> value.toLong()
                    is Boolean -> if (value) 1 else 0
                    null -> 0
                    else -> value.toString().toLongOrNull() ?: 0
                }
            )
            's' -> out.append(value?.toString() ?: "")
            'f' -> {
                val number = when (value) {
                    is Number -> value.toDouble()
                    null -> 0.0
                    else -> value.toString().toDoubleOrNull() ?: 0.0
                }
                out.append(formatFixed(number, precision))
            }
            else -> {
                // Unknown specifier, keep as-is
                out.append('%').append(type)
            }
        }
    }

    return out.toString()
}

private fun formatFixed(value: Double, precision: Int?): String {
    val p = precision ?: 6
    if (p <= 0) return round(value).toLong().toString()

    val scale = 10.0.pow(p.toDouble())
    val rounded = round(value * scale) / scale
    val raw = rounded.toString()

    // Ensure fixed number of decimals
    val dot = raw.indexOf('.')
    if (dot < 0) return raw + "." + "0".repeat(p)
    val decimals = raw.length - dot - 1
    return when {
        decimals == p -> raw
        decimals < p -> raw + "0".repeat(p - decimals)
        else -> raw.substring(0, dot + 1 + p)
    }
}

