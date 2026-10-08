@file:OptIn(org.jetbrains.compose.resources.ExperimentalResourceApi::class)

package com.vtempe.ui.screens.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.vtempe.core.designsystem.theme.AiPalette
import com.vtempe.shared.domain.legal.LegalDocuments
import com.vtempe.ui.*
import com.vtempe.ui.presenter.HealthConsentPresenter
import com.vtempe.ui.screens.consent.LegalLink
import com.vtempe.ui.screens.rememberHealthConsentPresenter
import com.vtempe.ui.util.kmpFormat
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.compose.resources.stringResource

/**
 * Settings → "Legal and consents": links to the published documents and the health-data consent
 * switch. Withdrawing is confirmed and immediately disables every AI request (same gate as at
 * launch); granting again re-enables them.
 */
@Composable
internal fun LegalSettingsSection(presenter: HealthConsentPresenter = rememberHealthConsentPresenter()) {
    val state by presenter.state.collectAsState()
    var confirmWithdraw by remember { mutableStateOf(false) }
    val textColor = MaterialTheme.colorScheme.onSurface

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.97f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
        shape = MaterialTheme.shapes.large
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                stringResource(Res.string.settings_legal_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = textColor
            )
            LegalLink(stringResource(Res.string.legal_privacy_policy), LegalDocuments.PRIVACY_POLICY_URL)
            LegalLink(stringResource(Res.string.legal_terms_of_use), LegalDocuments.TERMS_OF_USE_URL)
            LegalLink(stringResource(Res.string.legal_health_consent_document), LegalDocuments.HEALTH_DATA_CONSENT_URL)
            HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        stringResource(Res.string.settings_health_consent_label),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = textColor
                    )
                    Text(
                        consentStatusText(state.granted, state.decidedAtMillis),
                        style = MaterialTheme.typography.bodySmall,
                        color = textColor.copy(alpha = 0.65f)
                    )
                }
                Switch(
                    checked = state.granted,
                    onCheckedChange = { enable -> if (enable) presenter.grant() else confirmWithdraw = true },
                    colors = SwitchDefaults.colors(checkedTrackColor = AiPalette.DeepAccent)
                )
            }
        }
    }

    if (confirmWithdraw) {
        AlertDialog(
            onDismissRequest = { confirmWithdraw = false },
            title = { Text(stringResource(Res.string.settings_health_consent_withdraw_title)) },
            text = { Text(stringResource(Res.string.settings_health_consent_withdraw_body)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmWithdraw = false
                    presenter.withdraw()
                }) { Text(stringResource(Res.string.settings_health_consent_withdraw_confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmWithdraw = false }) {
                    Text(stringResource(Res.string.settings_health_consent_withdraw_cancel))
                }
            }
        )
    }
}

@Composable
private fun consentStatusText(granted: Boolean, decidedAtMillis: Long?): String {
    if (!granted || decidedAtMillis == null) return stringResource(Res.string.settings_health_consent_declined)
    val date = Instant.fromEpochMilliseconds(decidedAtMillis).toLocalDateTime(TimeZone.currentSystemDefault()).date
    return stringResource(Res.string.settings_health_consent_granted).kmpFormat(date.toString())
}
