package com.vtempe.server.features.ai.access

/**
 * Who may reach the paid LLM tier. Evaluated on the server only — the client's `llmMode` can
 * ask for the cheaper free tier, never raise itself above what this policy grants.
 */
enum class PaidAccessPolicy {
    /** Every admitted caller, guests included — the pre-billing testing phase. */
    EVERYONE,

    /** Any caller with a verified Firebase session; guests stay on free. */
    AUTHENTICATED,

    /** Only users whose entitlement is ACTIVE or GRACE. The secure default. */
    ENTITLEMENT;

    companion object {
        fun parse(raw: String?): PaidAccessPolicy? =
            raw?.trim()?.takeIf { it.isNotEmpty() }?.let { value ->
                entries.firstOrNull { it.name.equals(value, ignoreCase = true) }
            }
    }
}

/**
 * Configuration of the `/ai` access check (see [AiAccessResolver]).
 *
 * Every default is the closed one: a missing APP_SECRET disables AI instead of opening it
 * (VT-AUD-005), and an unset/unknown paid policy means "subscribers only" (VT-AUD-004).
 * Opening anything up is always an explicit, visible setting.
 */
data class AiAccessConfig(
    /** Shared app-build token expected in `X-App-Token`; null = not configured. */
    val appSecret: String?,
    /** Dev-only escape hatch: with no APP_SECRET, skip the app-token check instead of refusing
     *  every AI call. User authentication and the paid policy still apply. */
    val allowMissingAppSecret: Boolean = false,
    /** Callers without an `Authorization` header (the onboarding "Skip" path) may use AI. */
    val guestAccessEnabled: Boolean = true,
    val paidAccessPolicy: PaidAccessPolicy = PaidAccessPolicy.ENTITLEMENT,
) {
    companion object {
        const val APP_SECRET = "APP_SECRET"
        const val ALLOW_MISSING_APP_SECRET = "AI_ALLOW_MISSING_APP_SECRET"
        const val GUEST_ACCESS = "AI_GUEST_ACCESS"
        const val PAID_ACCESS = "AI_PAID_ACCESS"

        fun from(env: (String) -> String?): AiAccessConfig = AiAccessConfig(
            appSecret = env(APP_SECRET)?.takeIf { it.isNotBlank() },
            allowMissingAppSecret = env(ALLOW_MISSING_APP_SECRET).isTrue(default = false),
            guestAccessEnabled = env(GUEST_ACCESS).isTrue(default = true),
            paidAccessPolicy = PaidAccessPolicy.parse(env(PAID_ACCESS)) ?: PaidAccessPolicy.ENTITLEMENT,
        )

        private fun String?.isTrue(default: Boolean): Boolean =
            this?.trim()?.takeIf { it.isNotEmpty() }?.equals("true", ignoreCase = true) ?: default
    }
}
