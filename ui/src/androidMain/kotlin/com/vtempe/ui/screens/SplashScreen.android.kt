package com.vtempe.ui.screens

import com.vtempe.shared.domain.repository.ProfileRepository
import com.vtempe.ui.navigation.Destination
import org.koin.core.context.GlobalContext

actual suspend fun determineStartDestination(): Destination {
    val koin = GlobalContext.getOrNull()
    // Before reading the profile: an interrupted account switch may have left another account's data.
    reconcileAccountData(koin?.getOrNull())

    val profileRepository = runCatching {
        koin?.get<ProfileRepository>()
    }.getOrNull()

    val hasProfile = runCatching {
        profileRepository?.getProfile() != null
    }.getOrDefault(false)

    return if (hasProfile) Destination.Home else Destination.Welcome
}
