@file:OptIn(
    org.jetbrains.compose.resources.ExperimentalResourceApi::class,
    androidx.compose.foundation.layout.ExperimentalLayoutApi::class,
    androidx.compose.material3.ExperimentalMaterial3Api::class
)

package com.vtempe.ui.screens.onboarding

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.vtempe.core.designsystem.theme.AiPalette
import com.vtempe.ui.*
import com.vtempe.ui.presenter.OnboardingPresenter
import com.vtempe.ui.presenter.OnboardingState
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun LanguageStep(state: OnboardingState, presenter: OnboardingPresenter) {
    val languageOptions = listOf(
        "system" to stringResource(Res.string.settings_language_system),
        "en-US" to stringResource(Res.string.language_en),
        "ru-RU" to stringResource(Res.string.language_ru)
    )
    StepTitle(stringResource(Res.string.label_language))
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        languageOptions.forEach { (tag, label) ->
            ModernChip(
                selected = state.languageTag == tag,
                label = label,
                onClick = { presenter.setLanguage(tag) }
            )
        }
    }

    Spacer(Modifier.height(8.dp))
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
                presenter.update { it.copy(analyticsConsent = !it.analyticsConsent) }
            }
    ) {
        Checkbox(
            checked = state.analyticsConsent,
            onCheckedChange = { presenter.update { s -> s.copy(analyticsConsent = it) } },
            colors = CheckboxDefaults.colors(checkedColor = AiPalette.Primary)
        )
        Column(modifier = Modifier.padding(start = 4.dp)) {
            Text(
                stringResource(Res.string.analytics_consent_accept),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = onCard
            )
            Text(
                stringResource(Res.string.analytics_consent_hint),
                style = MaterialTheme.typography.bodySmall,
                color = onCard.copy(alpha = 0.65f)
            )
        }
    }
}
