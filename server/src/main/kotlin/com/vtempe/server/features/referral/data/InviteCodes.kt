package com.vtempe.server.features.referral.data

import java.security.SecureRandom

/**
 * Invite codes: 8 characters from an alphabet without look-alikes (no 0/O, 1/I/L), so a code
 * read aloud or typed from a screenshot survives. 31^8 ~ 8.5e11 codes. Codes are not secrets -
 * they are meant to be shared - but random, so they reveal nothing about the user.
 */
object InviteCodes {
    const val ALPHABET = "ABCDEFGHJKMNPQRSTUVWXYZ23456789"
    const val LENGTH = 8
    private const val MAX_INPUT = 64
    private val random = SecureRandom()

    fun generate(): String = buildString(LENGTH) { repeat(LENGTH) { append(ALPHABET[random.nextInt(ALPHABET.length)]) } }

    /** Accepts what people type or paste ("abcd-efgh", spaces, lower case); null if it cannot be a code. */
    fun normalize(input: String): String? {
        if (input.length > MAX_INPUT) return null
        // Char by char: String.uppercase() can lengthen text ("ß" -> "SS") and forge a valid code.
        val code = input.filterNot { it.isWhitespace() || it == '-' }.map { it.uppercaseChar() }.joinToString("")
        return code.takeIf { it.length == LENGTH && it.all { ch -> ch in ALPHABET } }
    }
}
