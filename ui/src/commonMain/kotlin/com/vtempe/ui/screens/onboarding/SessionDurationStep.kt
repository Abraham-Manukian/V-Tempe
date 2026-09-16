@file:OptIn(
    org.jetbrains.compose.resources.ExperimentalResourceApi::class,
    androidx.compose.foundation.layout.ExperimentalLayoutApi::class,
    androidx.compose.material3.ExperimentalMaterial3Api::class
)

package com.vtempe.ui.screens.onboarding

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.vtempe.core.designsystem.theme.AiPalette
import com.vtempe.ui.*
import com.vtempe.ui.presenter.OnboardingPresenter
import com.vtempe.ui.presenter.OnboardingState
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun SessionDurationStep(state: OnboardingState, presenter: OnboardingPresenter) {
    StepTitle(stringResource(Res.string.label_session_duration))
    Text(
        text = stringResource(Res.string.session_duration_hint),
        style = MaterialTheme.typography.bodyMedium,
        color = onCard.copy(alpha = 0.72f)
    )
    Spacer(Modifier.height(4.dp))
    val durationOptions = listOf(
        30 to (stringResource(Res.string.session_30min) to stringResource(Res.string.session_30min_desc)),
        45 to (stringResource(Res.string.session_45min) to stringResource(Res.string.session_45min_desc)),
        60 to (stringResource(Res.string.session_60min) to stringResource(Res.string.session_60min_desc)),
        90 to (stringResource(Res.string.session_90min) to stringResource(Res.string.session_90min_desc))
    )
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        durationOptions.forEach { (mins, labels) ->
            val (label, desc) = labels
            val selected = state.sessionDurationMins == mins
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { presenter.update { it.copy(sessionDurationMins = mins) } },
                colors = CardDefaults.cardColors(
                    containerColor = if (selected) AiPalette.Primary.copy(alpha = 0.12f)
                    else Color.White
                ),
                border = if (selected) BorderStroke(2.dp, AiPalette.Primary)
                else BorderStroke(1.dp, AiPalette.Outline.copy(alpha = 0.25f)),
                shape = MaterialTheme.shapes.medium
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        label,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (selected) AiPalette.Primary else onCard
                    )
                    Text(
                        desc,
                        style = MaterialTheme.typography.bodySmall,
                        color = onCard.copy(alpha = 0.7f)
                    )
                }
            }
        }
    }
}
