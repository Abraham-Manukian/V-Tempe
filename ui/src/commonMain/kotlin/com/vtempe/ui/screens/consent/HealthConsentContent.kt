@file:OptIn(org.jetbrains.compose.resources.ExperimentalResourceApi::class)

package com.vtempe.ui.screens.consent

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.vtempe.core.designsystem.theme.AiPalette
import com.vtempe.shared.domain.legal.LegalDocuments
import com.vtempe.ui.*
import io.github.aakira.napier.Napier
import org.jetbrains.compose.resources.stringResource

/**
 * The separate, never pre-checked consent checkbox shown on the last onboarding step.
 * Ticking the box is the user's explicit action; the presenter refuses to finish without it.
 */
@Composable
internal fun HealthConsentCheckbox(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    textColor: Color
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            stringResource(Res.string.health_consent_title),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = textColor
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().clickable { onCheckedChange(!checked) }
        ) {
            Checkbox(
                checked = checked,
                onCheckedChange = onCheckedChange,
                colors = CheckboxDefaults.colors(checkedColor = AiPalette.Primary)
            )
            Text(
                stringResource(Res.string.health_consent_checkbox),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = textColor,
                modifier = Modifier.padding(start = 4.dp)
            )
        }
        HealthConsentDetails(textColor = textColor)
    }
}

/** Expandable summary of what is sent where, plus links to the full documents. */
@Composable
internal fun HealthConsentDetails(textColor: Color, initiallyExpanded: Boolean = false) {
    var expanded by rememberSaveable { mutableStateOf(initiallyExpanded) }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        TextButton(onClick = { expanded = !expanded }) {
            Text(
                stringResource(
                    if (expanded) Res.string.health_consent_details_hide else Res.string.health_consent_details_show
                ),
                color = AiPalette.DeepAccent,
                fontWeight = FontWeight.SemiBold
            )
        }
        AnimatedVisibility(visible = expanded) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    stringResource(Res.string.health_consent_summary),
                    style = MaterialTheme.typography.bodySmall,
                    color = textColor.copy(alpha = 0.75f)
                )
                LegalLink(stringResource(Res.string.legal_health_consent_document), LegalDocuments.HEALTH_DATA_CONSENT_URL)
                LegalLink(stringResource(Res.string.legal_privacy_policy), LegalDocuments.PRIVACY_POLICY_URL)
            }
        }
    }
}

/** Opens a published legal document in the system browser. */
@Composable
internal fun LegalLink(label: String, url: String) {
    val uriHandler = LocalUriHandler.current
    TextButton(
        onClick = {
            // No browser / malformed placeholder URL must not crash the app.
            runCatching { uriHandler.openUri(url) }
                .onFailure { Napier.w(tag = "Legal", message = "cannot open legal document", throwable = it) }
        }
    ) {
        Text(label, color = AiPalette.DeepAccent, textDecoration = TextDecoration.Underline)
    }
}
