package com.vtempe.server

import com.vtempe.server.features.referral.data.InviteCodes
import com.vtempe.server.features.referral.data.ReferralConfig
import com.vtempe.server.features.referral.domain.model.RedeemOffer
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.Duration

/** A wrong referral setting must stop the server, never pay out at a wrong rate. */
class ReferralConfigTest {
    private fun policy(vararg env: Pair<String, String>) = ReferralConfig.from { key -> env.toMap()[key] }

    @Test
    fun `defaults are the owner's rules with nothing to buy yet`() {
        val policy = policy()
        assertEquals(20, policy.sharePercent)
        assertEquals(Duration.ofDays(30), policy.hold)
        assertTrue(policy.offers.isEmpty())
        assertEquals(5980L, policy.shareOf(29900))
        assertEquals(99L, policy.shareOf(499), "rounds down to a whole kopeck/cent")
    }

    @Test
    fun `offers are parsed strictly`() {
        assertEquals(listOf(RedeemOffer("RUB", 49900, 30), RedeemOffer("USD", 500, 30)),
            policy("REFERRAL_REDEEM_OFFERS" to " rub:49900:30 , USD:500:30").offers)
        for (bad in listOf("RUB:0:30", "RUB:100", "RUB:x:30", "RUB:100:0", "RUBLE:100:30", "RUB:1:30,RUB:2:30")) {
            assertThrows<IllegalArgumentException>(bad) { policy("REFERRAL_REDEEM_OFFERS" to bad) }
        }
    }

    @Test
    fun `share and hold are validated`() {
        for (bad in listOf("101", "-5", "abc", "4294967316")) {
            assertThrows<RuntimeException>(bad) { policy("REFERRAL_SHARE_PERCENT" to bad) }
        }
        assertEquals(0, policy("REFERRAL_SHARE_PERCENT" to "0").sharePercent, "0 turns rewards off")
        for (bad in listOf("-1", "366", "1000000000000")) {
            assertThrows<RuntimeException>(bad) { policy("REFERRAL_HOLD_DAYS" to bad) }
        }
        assertEquals(Duration.ZERO, policy("REFERRAL_HOLD_DAYS" to "0").hold)
        assertEquals(Duration.ofDays(365), policy("REFERRAL_HOLD_DAYS" to "365").hold)
    }

    @Test
    fun `codes use an alphabet without look-alikes and accept typed variants`() {
        repeat(200) {
            val code = InviteCodes.generate()
            assertEquals(8, code.length)
            assertTrue(code.all { it in InviteCodes.ALPHABET }, code)
        }
        assertEquals("BRSKCDM2", InviteCodes.normalize(" brsk-cdm2 "))
        assertNull(InviteCodes.normalize("BRSKCDM0"))
        assertNull(InviteCodes.normalize("BRSKCDM"))
        assertNull(InviteCodes.normalize("B".repeat(100)))
        assertNull(InviteCodes.normalize("BRSKCDß"), "ß must not become SS and forge a code")
        assertNull(InviteCodes.normalize("ВRSKCDM2"), "Cyrillic look-alike")
        assertNull(InviteCodes.normalize("ＢRSKCDM2"), "full-width letter")
        assertEquals("BRSKCDM2", InviteCodes.normalize("brsk	cdm2"))
    }
}
