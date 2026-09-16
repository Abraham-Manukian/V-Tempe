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
import com.vtempe.ui.presenter.TRAINING_FOCUS_FAT_LOSS
import com.vtempe.ui.presenter.TRAINING_FOCUS_GENERAL
import com.vtempe.ui.presenter.TRAINING_FOCUS_HYPERTROPHY
import com.vtempe.ui.presenter.TRAINING_FOCUS_STRENGTH
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun TrainingFocusStep(state: OnboardingState, presenter: OnboardingPresenter) {
    StepTitle(stringResource(Res.string.label_training_focus))
    Text(
        text = stringResource(Res.string.training_focus_hint),
        style = MaterialTheme.typography.bodyMedium,
        color = onCard.copy(alpha = 0.72f)
    )
    Spacer(Modifier.height(4.dp))
    val focusOptions = listOf(
        TRAINING_FOCUS_HYPERTROPHY to Triple(
            stringResource(Res.string.training_focus_hypertrophy),
            stringResource(Res.string.training_focus_hypertrophy_reps),
            stringResource(Res.string.training_focus_hypertrophy_desc)
        ),
        TRAINING_FOCUS_STRENGTH to Triple(
            stringResource(Res.string.training_focus_strength),
            stringResource(Res.string.training_focus_strength_reps),
            stringResource(Res.string.training_focus_strength_desc)
        ),
        TRAINING_FOCUS_GENERAL to Triple(
            stringResource(Res.string.training_focus_general),
            stringResource(Res.string.training_focus_general_reps),
            stringResource(Res.string.training_focus_general_desc)
        ),
        TRAINING_FOCUS_FAT_LOSS to Triple(
            stringResource(Res.string.training_focus_fat_loss),
            stringResource(Res.string.training_focus_fat_loss_reps),
            stringResource(Res.string.training_focus_fat_loss_desc)
        )
    )
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        focusOptions.forEach { (focus, labels) ->
            val (name, reps, desc) = labels
            val selected = state.trainingFocus == focus
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { presenter.update { it.copy(trainingFocus = focus) } },
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
                        name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (selected) AiPalette.Primary else onCard
                    )
                    Text(
                        reps,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = if (selected) AiPalette.Primary.copy(alpha = 0.8f) else onCard.copy(alpha = 0.55f)
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
