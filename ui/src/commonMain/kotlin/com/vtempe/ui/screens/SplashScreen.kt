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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.ui.text.style.TextAlign
import com.vtempe.ui.Res
import com.vtempe.ui.common_retry
import com.vtempe.ui.startup_storage_error
import org.jetbrains.compose.resources.stringResource
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
    // Bumped by Retry after the stored data could not be read (VT-AUD-077).
    var attempt by remember { mutableIntStateOf(0) }
    var storageFailed by remember { mutableStateOf(false) }
    LaunchedEffect(attempt) {
        storageFailed = false
        if (attempt == 0) scale.animateTo(1f, animationSpec = tween(600))
        when (val decision = decideStart(startupDependencies())) {
            is StartDecision.Go -> {
                onReady(decision.destination)
                decided.value = true
            }
            StartDecision.StorageFailed -> storageFailed = true
        }
    }
    if (!decided.value) {
        Box(
            Modifier
                .fillMaxSize()
                .background(AiGradients.lavenderMist()),
            contentAlignment = Alignment.Center
        ) {
            if (storageFailed) {
                Column(
                    modifier = Modifier.padding(horizontal = 32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        stringResource(Res.string.startup_storage_error),
                        color = AiPalette.OnGradient,
                        textAlign = TextAlign.Center
                    )
                    OutlinedButton(onClick = { attempt++ }) {
                        Text(stringResource(Res.string.common_retry), color = AiPalette.OnGradient)
                    }
                }
            } else {
                CircularProgressIndicator(
                    modifier = Modifier
                        .size(42.dp)
                        .scale(scale.value),
                    color = AiPalette.OnGradient
                )
            }
        }
    }
}

/** How this platform reaches launch dependencies; null while DI is not started. */
expect fun startupDependencies(): StartupDependencies?

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
