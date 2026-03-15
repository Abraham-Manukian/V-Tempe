package com.vtempe.ui.screens

import com.vtempe.shared.data.di.KoinProvider
import com.vtempe.shared.domain.repository.ProfileRepository
import com.vtempe.ui.navigation.Routes

actual suspend fun determineStartDestination(): String {
    val koin = KoinProvider.koin ?: return Routes.Onboarding
    val profileRepository = runCatching { koin.get<ProfileRepository>() }.getOrNull()
        ?: return Routes.Onboarding

    val hasProfile = runCatching { profileRepository.getProfile() != null }.getOrDefault(false)
    return if (hasProfile) Routes.Home else Routes.Onboarding
}
