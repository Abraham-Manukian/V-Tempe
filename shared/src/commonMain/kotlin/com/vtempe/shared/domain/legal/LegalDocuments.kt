package com.vtempe.shared.domain.legal

/**
 * Single source of truth for the published legal documents (see docs/legal/).
 *
 * The URLs are PLACEHOLDERS until the owner publishes docs/legal/site/ on a public domain —
 * replace [BASE_URL] only, every link in the app is derived from it.
 */
object LegalDocuments {
    /**
     * Version of the health-data / cross-border consent text (docs/legal/health-data-consent.md).
     * Bump it when that text changes materially: every stored decision for an older version
     * counts as "not asked", so users are asked again before AI features run.
     */
    const val HEALTH_DATA_CONSENT_VERSION = "2026-10-08"

    // TODO(owner): replace with the real public URL of docs/legal/site/.
    const val BASE_URL = "https://vtempe.example/legal"

    const val PRIVACY_POLICY_URL = "$BASE_URL/"
    const val TERMS_OF_USE_URL = "$BASE_URL/terms.html"
    const val HEALTH_DATA_CONSENT_URL = "$BASE_URL/health-data-consent.html"
    const val DELETE_ACCOUNT_URL = "$BASE_URL/delete-account.html"
}
