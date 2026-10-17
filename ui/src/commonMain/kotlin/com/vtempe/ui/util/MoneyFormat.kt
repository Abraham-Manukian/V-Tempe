package com.vtempe.ui.util

import kotlin.math.absoluteValue

/**
 * Formats a server amount in minor units (kopecks, cents) for display: `59,80 ₽`, `$5.00`,
 * `12.34 EUR`. Every currency the server uses has two decimals today (RUB, USD); others fall back
 * to the ISO code. Negative balances (a refund after the hold) keep their sign.
 */
fun formatMoney(minor: Long, currency: String): String {
    val sign = if (minor < 0) "-" else ""
    val whole = minor.absoluteValue / 100
    val cents = (minor.absoluteValue % 100).toString().padStart(2, '0')
    return when (currency.uppercase()) {
        "RUB" -> "$sign$whole,$cents ₽"
        "USD" -> "$sign$$whole.$cents"
        else -> "$sign$whole.$cents ${currency.uppercase()}"
    }
}
