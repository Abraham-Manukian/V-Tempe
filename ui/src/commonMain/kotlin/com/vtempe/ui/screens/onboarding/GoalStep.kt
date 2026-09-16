@file:OptIn(
    org.jetbrains.compose.resources.ExperimentalResourceApi::class,
    androidx.compose.foundation.layout.ExperimentalLayoutApi::class,
    androidx.compose.material3.ExperimentalMaterial3Api::class
)

package com.vtempe.ui.screens.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.vtempe.core.designsystem.theme.AiPalette
import com.vtempe.shared.domain.model.Goal
import com.vtempe.ui.*
import com.vtempe.ui.presenter.OnboardingPresenter
import com.vtempe.ui.presenter.OnboardingState
import com.vtempe.ui.util.kmpFormat
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun GoalStep(state: OnboardingState, presenter: OnboardingPresenter) {
    StepTitle(stringResource(Res.string.label_goal))
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Goal.entries.forEach { goal ->
            val label = when (goal) {
                Goal.LOSE_FAT -> stringResource(Res.string.goal_lose_fat)
                Goal.MAINTAIN -> stringResource(Res.string.goal_maintain)
                Goal.GAIN_MUSCLE -> stringResource(Res.string.goal_gain_muscle)
            }
            ModernChip(
                selected = state.goal == goal,
                label = label,
                onClick = { presenter.update { it.copy(goal = goal) } }
            )
        }
    }
    Spacer(Modifier.height(8.dp))
    Text(
        stringResource(Res.string.label_experience).kmpFormat(state.experienceLevel),
        style = MaterialTheme.typography.titleSmall,
        color = onCard
    )
    val expLabel = when (state.experienceLevel) {
        1 -> stringResource(Res.string.experience_level_1)
        2 -> stringResource(Res.string.experience_level_2)
        3 -> stringResource(Res.string.experience_level_3)
        4 -> stringResource(Res.string.experience_level_4)
        else -> stringResource(Res.string.experience_level_5)
    }
    Text(
        expLabel,
        style = MaterialTheme.typography.bodyMedium,
        fontWeight = FontWeight.SemiBold,
        color = AiPalette.Primary
    )
    Slider(
        value = state.experienceLevel.toFloat(),
        onValueChange = { lvl -> presenter.update { it.copy(experienceLevel = lvl.toInt().coerceIn(1, 5)) } },
        valueRange = 1f..5f,
        steps = 3,
        colors = SliderDefaults.colors(
            thumbColor = AiPalette.Primary,
            activeTrackColor = AiPalette.Primary,
            inactiveTrackColor = AiPalette.Primary.copy(alpha = 0.2f)
        )
    )
    Text(
        stringResource(Res.string.experience_hint),
        style = MaterialTheme.typography.bodySmall,
        color = onCard.copy(alpha = 0.6f)
    )
}
