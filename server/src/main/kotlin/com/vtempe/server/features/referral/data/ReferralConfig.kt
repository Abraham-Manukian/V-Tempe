package com.vtempe.server.features.referral.data

import com.vtempe.server.features.referral.domain.model.RedeemOffer
import com.vtempe.server.features.referral.domain.model.ReferralPolicy
import org.slf4j.LoggerFactory
import java.time.Duration

/**
 * Referral policy from the environment:
 * - REFERRAL_SHARE_PERCENT (default 20, 0..100; 0 turns rewards off): the referrer's share of the
 *   invited user's first payment.
 * - REFERRAL_HOLD_DAYS (default 30, at most 365): how long a share stays pending (a refund in that
 *   time cancels it).
 * - REFERRAL_REDEEM_OFFERS: what the balance can buy, `CUR:priceMinor:days` comma-separated,
 *   e.g. `RUB:49900:30,USD:500:30`. Empty (the default): balances accrue, nothing can be bought yet.
 * An invalid value is a configuration error: Application.module() reads the policy at startup, so
 * the revision fails to start (and Cloud Run keeps the previous one) instead of failing payments.
 *
 * Rollout note: a redemption writes `source = REFERRAL_BALANCE` into `entitlements`. Revisions
 * before the referral release cannot read that value, so set REFERRAL_REDEEM_OFFERS only once no
 * older revision can serve traffic or be rolled back to (this release reads unknown sources).
 */
object ReferralConfig {
    private val logger = LoggerFactory.getLogger(ReferralConfig::class.java)

    fun from(env: (String) -> String?): ReferralPolicy {
        val share = integer(env, "REFERRAL_SHARE_PERCENT")?.let(Math::toIntExact) ?: DEFAULT_SHARE_PERCENT
        val holdDays = integer(env, "REFERRAL_HOLD_DAYS") ?: DEFAULT_HOLD_DAYS
        require(holdDays in 0..ReferralPolicy.MAX_HOLD.toDays()) { "REFERRAL_HOLD_DAYS must be 0..${ReferralPolicy.MAX_HOLD.toDays()}" }
        val offers = parseOffers(env("REFERRAL_REDEEM_OFFERS"))
        val policy = ReferralPolicy(share, Duration.ofDays(holdDays), offers)
        logger.info("Referral policy: share={}%, hold={}d, redeem offers={}", share, holdDays,
            offers.joinToString { "${it.currency} ${it.priceMinor}/${it.periodDays}d" }.ifEmpty { "none" })
        return policy
    }

    private fun integer(env: (String) -> String?, key: String): Long? =
        env(key)?.trim()?.takeIf { it.isNotEmpty() }?.let { it.toLongOrNull() ?: throw IllegalArgumentException("$key must be an integer") }

    fun parseOffers(raw: String?): List<RedeemOffer> {
        if (raw.isNullOrBlank()) return emptyList()
        val offers = raw.split(',').map { it.trim() }.filter { it.isNotEmpty() }.map { item ->
            val parts = item.split(':')
            require(parts.size == 3) { "REFERRAL_REDEEM_OFFERS item '$item' must be CUR:priceMinor:days" }
            RedeemOffer(
                currency = parts[0].trim().uppercase(),
                priceMinor = parts[1].trim().toLongOrNull() ?: throw IllegalArgumentException("price in '$item' is not a number"),
                periodDays = parts[2].trim().toIntOrNull() ?: throw IllegalArgumentException("days in '$item' is not a number"),
            )
        }
        require(offers.map { it.currency }.toSet().size == offers.size) { "REFERRAL_REDEEM_OFFERS lists a currency twice" }
        return offers
    }

    const val DEFAULT_SHARE_PERCENT = 20
    const val DEFAULT_HOLD_DAYS = 30L
}
