package com.vtempe.server

import com.vtempe.server.features.ai.data.privacy.DirectIdentifierRedactor
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.random.Random

/** VT-AUD-011: the redactor is linear on hostile input and still removes the same identifiers. */
class DirectIdentifierRedactorCostTest {

    /** The full redaction as it was before the fix — kept only as the reference for identical output. */
    private fun referenceRedact(text: String): String = text
        .replace(Regex("[A-Za-z0-9._%+-]+@[A-Za-z0-9-]+(?:\\.[A-Za-z0-9-]+)*\\.[A-Za-z]{2,}"), DirectIdentifierRedactor.EMAIL_PLACEHOLDER)
        .replace(Regex("(?<![\\w+])\\+\\d(?:[\\s().-]*\\d){9,14}(?!\\d)"), DirectIdentifierRedactor.PHONE_PLACEHOLDER)
        .replace(Regex("(?<![\\w+])8(?:[\\s().-]*\\d){10}(?!\\d)"), DirectIdentifierRedactor.PHONE_PLACEHOLDER)
        .replace(Regex("(?<![\\w.@])@[A-Za-z][A-Za-z0-9_]{3,31}\\b"), DirectIdentifierRedactor.HANDLE_PLACEHOLDER)

    @Test
    fun `redaction output is identical to the former implementation`() {
        val random = Random(20261010)
        val alphabet = "aZb0.-_+%@@ я.co8(1"
        repeat(30_000) {
            val text = String(CharArray(random.nextInt(1, 48)) { alphabet[random.nextInt(alphabet.length)] })
            assertEquals(referenceRedact(text), DirectIdentifierRedactor.redact(text), "input: '$text'")
        }
    }

    @Test
    fun `known addresses, phones and handles are still removed`() {
        val text = "write anna.k+fit@mail.co.uk or a@b.ru, call +7 (900) 123-45-67, tg @coach_anna; 3x10 at 120/80"
        val redacted = DirectIdentifierRedactor.redact(text)
        assertEquals(
            "write [email] or [email], call [phone], tg [handle]; 3x10 at 120/80",
            redacted
        )
        assertEquals("user@host", DirectIdentifierRedactor.redact("user@host"), "no TLD — not an address")
        assertEquals("[email].x", DirectIdentifierRedactor.redact("a@b.com.x"))
    }

    @Test
    fun `hostile inputs finish in bounded time`() {
        val size = 200_000
        val inputs = mapOf(
            "letters without @" to "x".repeat(size),
            "local run then @" to "x".repeat(size) + "@",
            "many @" to "a@".repeat(size / 2),
            "near-miss domains" to "a@b.".repeat(size / 4),
            "dotted local part" to "a.".repeat(size / 2) + "@b",
            "plus and digits" to "+1".repeat(size / 2),
            "eights with spaces" to "8 ".repeat(size / 2),
            "handle-like" to "@abc".repeat(size / 4),
        )
        inputs.forEach { (name, input) ->
            val started = System.nanoTime()
            DirectIdentifierRedactor.redact(input)
            val ms = (System.nanoTime() - started) / 1_000_000
            // The quadratic version needed ~2.5 s for 20 000 letters; linear work on 10x that is
            // a few ms. The generous ceiling keeps the check meaningful without being flaky.
            assertTrue(ms < 3_000, "$name took $ms ms")
        }
    }
}
