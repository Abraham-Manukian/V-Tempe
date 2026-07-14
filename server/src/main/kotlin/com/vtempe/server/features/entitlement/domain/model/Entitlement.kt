package com.vtempe.server.features.entitlement.domain.model

import java.time.Instant

/** Where a grant of access originated — the app never needs to know which one, only
 *  [EntitlementService] and the provider-specific verifiers (Google Play / App Store /
 *  YooKassa) care. */
enum class PaymentSource { GOOGLE_PLAY, APP_STORE, YOOKASSA }

enum class EntitlementStatus { ACTIVE, GRACE, EXPIRED }

/**
 * The current subscription state for one user, computed from [expiresAt] rather than a
 * separately-persisted status flag — self-healing (no cron needed to "expire" a row) and
 * immune to the status column silently drifting from the timestamp it should be derived from.
 */
data class Entitlement(
    val userId: String,
    val status: EntitlementStatus,
    val expiresAt: Instant?,
    val source: PaymentSource?,
    val externalRef: String?
)
