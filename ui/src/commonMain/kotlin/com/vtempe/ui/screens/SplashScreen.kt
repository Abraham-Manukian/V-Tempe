package com.vtempe.ui.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.unit.dp
import com.vtempe.core.designsystem.theme.AiGradients
import com.vtempe.core.designsystem.theme.AiPalette
import com.vtempe.shared.domain.account.AccountSession
import com.vtempe.shared.domain.consent.HealthDataConsentManager
import com.vtempe.shared.domain.consent.HealthDataConsentStatus
import com.vtempe.ui.navigation.Destination
import io.github.aakira.napier.Napier
import kotlinx.coroutines.CancellationException

@Composable
fun SplashScreen(onReady: (Destination) -> Unit = {}) {
    val scale = remember { Animatable(0.8f) }
    val decided: MutableState<Boolean> = remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        scale.animateTo(1f, animationSpec = tween(600))
        val destination = determineStartDestination()
        onReady(destination)
        decided.value = true
    }
    if (!decided.value) {
        Box(
            Modifier
                .fillMaxSize()
                .background(AiGradients.lavenderMist()),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator(
                modifier = Modifier
                    .size(42.dp)
                    .scale(scale.value),
                color = AiPalette.OnGradient
            )
        }
    }
}

expect suspend fun determineStartDestination(): Destination

/**
 * Where a user who already has a profile lands. Users onboarded before the health-data consent
 * existed (or before its current version) are asked once before reaching Home; until they
 * grant it, the AI gates keep their health data on the device.
 */
internal fun destinationForExistingProfile(consent: HealthDataConsentManager?): Destination =
    if (consent?.status() == HealthDataConsentStatus.NOT_ASKED) Destination.HealthConsent else Destination.Home

/** Launch must not fail because of sync; an unsettled state is retried on the next launch or sign-in. */
internal suspend fun reconcileAccountData(session: AccountSession?) {
    if (session == null) return
    try {
        session.reconcile()
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (error: Exception) {
        Napier.w(tag = "Account", message = "launch reconcile failed", throwable = error)
    }
}
