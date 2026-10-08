package com.vtempe.ui.screens

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.vtempe.shared.data.di.KoinProvider
import com.vtempe.shared.domain.consent.HealthDataConsentManager
import com.vtempe.ui.presenter.HealthConsentPresenter

// Synchronous and stateless beyond the stored decision, so no scope or lifecycle is needed.
@Composable
actual fun rememberHealthConsentPresenter(): HealthConsentPresenter = remember {
    val koin = requireNotNull(KoinProvider.koin) { "Koin is not started" }
    HealthConsentPresenter(koin.get<HealthDataConsentManager>())
}
