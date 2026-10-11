package com.vtempe.ui.screens

import com.vtempe.shared.domain.account.AccountSession
import com.vtempe.shared.domain.consent.HealthDataConsentManager
import com.vtempe.shared.domain.repository.ProfileRepository
import com.vtempe.ui.navigation.Destination
import io.github.aakira.napier.Napier
import kotlinx.coroutines.CancellationException

/** What launch found (VT-AUD-077). */
sealed interface StartDecision {
    data class Go(val destination: Destination) : StartDecision
    /** The stored data could not be opened or read; nothing was cleared, the user can retry. */
    data object StorageFailed : StartDecision
}

/** Platform lookups for launch; resolving one may itself fail (opening the database). */
class StartupDependencies(
    val accountSession: () -> AccountSession?,
    val profiles: () -> ProfileRepository,
    val consent: () -> HealthDataConsentManager?,
)

/**
 * Welcome only after the profile was actually read and is absent. A failure to open the database
 * or read the profile used to look like "no profile" and sent a returning user into first-run
 * onboarding; now it is [StartDecision.StorageFailed], and a dependency that fails to resolve no
 * longer escapes the splash screen.
 */
internal suspend fun decideStart(dependencies: StartupDependencies?): StartDecision {
    if (dependencies == null) return StartDecision.StorageFailed // DI not started
    return try {
        // Before reading the profile: an interrupted account switch may have left another account's data.
        reconcileAccountData(dependencies.accountSession())
        val profile = dependencies.profiles().getProfile()
        StartDecision.Go(if (profile != null) destinationForExistingProfile(dependencies.consent()) else Destination.Welcome)
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (error: Exception) {
        Napier.e(tag = "Startup", message = "Stored data could not be read: ${error::class.simpleName}")
        StartDecision.StorageFailed
    }
}
