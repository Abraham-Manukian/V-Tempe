package com.vtempe.ui.screens

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.vtempe.shared.domain.consent.HealthDataConsentManager
import com.vtempe.ui.presenter.HealthConsentPresenter
import org.koin.core.context.GlobalContext

// Synchronous and stateless beyond the stored decision, so no ViewModel is needed.
@Composable
actual fun rememberHealthConsentPresenter(): HealthConsentPresenter = remember {
    HealthConsentPresenter(GlobalContext.get().get<HealthDataConsentManager>())
}
