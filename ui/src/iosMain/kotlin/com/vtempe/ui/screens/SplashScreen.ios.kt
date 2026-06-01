package com.vtempe.ui.screens

import com.vtempe.shared.data.di.KoinProvider
import com.vtempe.shared.domain.repository.ProfileRepository
import com.vtempe.ui.navigation.Destination

actual suspend fun determineStartDestination(): Destination {
    val koin = KoinProvider.koin ?: return Destination.Onboarding
    val profileRepository = runCatching { koin.get<ProfileRepository>() }.getOrNull()
        ?: return Destination.Onboarding

    val hasProfile = runCatching { profileRepository.getProfile() != null }.getOrDefault(false)
    return if (hasProfile) Destination.Home else Destination.Onboarding
}
