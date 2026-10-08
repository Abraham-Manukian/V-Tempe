package com.vtempe.ui.screens

import com.vtempe.shared.data.di.KoinProvider
import com.vtempe.shared.domain.repository.ProfileRepository
import com.vtempe.ui.navigation.Destination

actual suspend fun determineStartDestination(): Destination {
    val koin = KoinProvider.koin ?: return Destination.Welcome
    // Before reading the profile: an interrupted account switch may have left another account's data.
    reconcileAccountData(koin.getOrNull())

    val profileRepository = runCatching { koin.get<ProfileRepository>() }.getOrNull()
        ?: return Destination.Welcome

    val hasProfile = runCatching { profileRepository.getProfile() != null }.getOrDefault(false)
    return if (hasProfile) destinationForExistingProfile(koin.getOrNull()) else Destination.Welcome
}
