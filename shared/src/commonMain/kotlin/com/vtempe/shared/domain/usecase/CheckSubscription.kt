package com.vtempe.shared.domain.usecase

import com.vtempe.shared.data.repo.LastKnownEntitlementStore
import com.vtempe.shared.domain.repository.AuthRepository
import com.vtempe.shared.domain.repository.EntitlementRepository
import com.vtempe.shared.domain.util.DataResult

/** The subscription as the server knows it (VT-AUD-053). */
sealed interface SubscriptionStatus {
    /** No account signed in: a subscription belongs to an account. */
    data object SignedOut : SubscriptionStatus
    /** [stale]: the server could not be asked now; this is the last answer it gave for this account. */
    data class Known(val active: Boolean, val expiresAt: String?, val stale: Boolean) : SubscriptionStatus
    /** Never confirmed for this account and the server cannot be asked now. */
    data object Unknown : SubscriptionStatus
}

/**
 * The single source of the subscription status shown to the user: the server's entitlement for
 * the signed-in account (YooKassa grants included), not a client-side store stub that said
 * "active" on iOS and "inactive" on Android whatever the server held.
 */
class CheckSubscription(
    private val auth: AuthRepository,
    private val entitlements: EntitlementRepository,
    private val lastKnown: LastKnownEntitlementStore,
) {
    suspend operator fun invoke(): SubscriptionStatus {
        val uid = auth.authState.value?.uid ?: return SubscriptionStatus.SignedOut
        return when (val result = entitlements.fetchEntitlement()) {
            is DataResult.Success -> {
                lastKnown.save(uid, result.data.active, result.data.expiresAt)
                SubscriptionStatus.Known(result.data.active, result.data.expiresAt, stale = false)
            }
            is DataResult.Failure -> lastKnown.load(uid)
                ?.let { SubscriptionStatus.Known(it.active, it.expiresAt, stale = true) }
                ?: SubscriptionStatus.Unknown
        }
    }
}
