@file:OptIn(org.jetbrains.compose.resources.ExperimentalResourceApi::class)

package com.vtempe.ui.screens.consent

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.vtempe.core.designsystem.components.BrandScreen
import com.vtempe.core.designsystem.theme.AiPalette
import com.vtempe.shared.domain.legal.LegalDocuments
import com.vtempe.ui.*
import com.vtempe.ui.presenter.HealthConsentPresenter
import com.vtempe.ui.screens.rememberHealthConsentPresenter
import org.jetbrains.compose.resources.stringResource

private val cardText = Color(0xFF1A1A1A)

/**
 * One-time blocking request for users who finished onboarding before the consent existed (or
 * before the current consent version). Either answer is recorded, so it is not shown again;
 * a decline leaves the AI coach off until the user grants consent in Settings.
 */
@Composable
fun HealthConsentScreen(
    onDone: () -> Unit,
    presenter: HealthConsentPresenter = rememberHealthConsentPresenter()
) {
    var showDeclined by rememberSaveable { mutableStateOf(false) }

    BrandScreen(Modifier.fillMaxSize().statusBarsPadding()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.98f)),
                elevation = CardDefaults.cardElevation(defaultElevation = 10.dp),
                shape = MaterialTheme.shapes.extraLarge,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        stringResource(Res.string.health_consent_title),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = cardText
                    )
                    Text(
                        stringResource(Res.string.health_consent_prompt_body),
                        style = MaterialTheme.typography.bodyMedium,
                        color = cardText
                    )
                    HealthConsentDetails(textColor = cardText, initiallyExpanded = true)
                    LegalLink(stringResource(Res.string.legal_terms_of_use), LegalDocuments.TERMS_OF_USE_URL)
                }
            }
            Button(
                onClick = {
                    presenter.grant()
                    onDone()
                },
                modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
                shape = MaterialTheme.shapes.large,
                colors = ButtonDefaults.buttonColors(
                    containerColor = AiPalette.DeepAccent,
                    contentColor = AiPalette.OnDeepAccent
                )
            ) { Text(stringResource(Res.string.health_consent_accept), fontWeight = FontWeight.Bold) }
            OutlinedButton(
                onClick = {
                    presenter.withdraw()
                    showDeclined = true
                },
                modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
                shape = MaterialTheme.shapes.large,
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)
            ) { Text(stringResource(Res.string.health_consent_decline), fontWeight = FontWeight.Bold) }
        }
    }

    if (showDeclined) {
        AlertDialog(
            onDismissRequest = onDone,
            title = { Text(stringResource(Res.string.health_consent_declined_title)) },
            text = { Text(stringResource(Res.string.health_consent_declined_body)) },
            confirmButton = {
                TextButton(onClick = onDone) { Text(stringResource(Res.string.health_consent_continue)) }
            }
        )
    }
}
