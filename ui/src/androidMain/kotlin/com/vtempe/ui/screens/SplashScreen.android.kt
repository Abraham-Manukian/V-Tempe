package com.vtempe.ui.screens

import com.vtempe.shared.domain.repository.ProfileRepository
import com.vtempe.ui.navigation.Routes
import org.koin.core.context.GlobalContext

actual suspend fun determineStartDestination(): String {
    val profileRepository = runCatching {
        GlobalContext.get().get<ProfileRepository>()
    }.getOrNull()

    val hasProfile = runCatching {
        profileRepository?.getProfile() != null
    }.getOrDefault(false)

    return if (hasProfile) Routes.Home else Routes.Onboarding
}
