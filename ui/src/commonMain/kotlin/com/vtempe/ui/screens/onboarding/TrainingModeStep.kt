@file:OptIn(
    org.jetbrains.compose.resources.ExperimentalResourceApi::class,
    androidx.compose.foundation.layout.ExperimentalLayoutApi::class,
    androidx.compose.material3.ExperimentalMaterial3Api::class
)

package com.vtempe.ui.screens.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import com.vtempe.ui.*
import com.vtempe.ui.presenter.OnboardingPresenter
import com.vtempe.ui.presenter.OnboardingState
import com.vtempe.ui.presenter.TRAINING_MODE_GYM
import com.vtempe.ui.presenter.TRAINING_MODE_HOME
import com.vtempe.ui.presenter.TRAINING_MODE_MIXED
import com.vtempe.ui.presenter.TRAINING_MODE_OUTDOOR
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun TrainingModeStep(state: OnboardingState, presenter: OnboardingPresenter) {
    val trainingModeOptions = listOf(
        TRAINING_MODE_GYM to stringResource(Res.string.training_mode_gym),
        TRAINING_MODE_HOME to stringResource(Res.string.training_mode_home),
        TRAINING_MODE_OUTDOOR to stringResource(Res.string.training_mode_outdoor),
        TRAINING_MODE_MIXED to stringResource(Res.string.training_mode_mixed)
    )
    StepTitle(stringResource(Res.string.label_training_mode))
    Text(
        text = stringResource(Res.string.training_mode_hint),
        style = MaterialTheme.typography.bodyMedium,
        color = onCard.copy(alpha = 0.72f)
    )
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        trainingModeOptions.forEach { (mode, label) ->
            ModernChip(
                selected = state.trainingMode == mode,
                label = label,
                onClick = { presenter.update { it.copy(trainingMode = mode) } }
            )
        }
    }
}
