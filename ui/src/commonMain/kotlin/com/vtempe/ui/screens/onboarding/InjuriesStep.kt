@file:OptIn(
    org.jetbrains.compose.resources.ExperimentalResourceApi::class,
    androidx.compose.foundation.layout.ExperimentalLayoutApi::class,
    androidx.compose.material3.ExperimentalMaterial3Api::class
)

package com.vtempe.ui.screens.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.vtempe.core.designsystem.theme.AiPalette
import com.vtempe.ui.*
import com.vtempe.ui.presenter.OnboardingPresenter
import com.vtempe.ui.presenter.OnboardingState
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun InjuriesStep(state: OnboardingState, presenter: OnboardingPresenter) {
    val inputColors = OutlinedTextFieldDefaults.colors(
        focusedBorderColor = AiPalette.Primary,
        unfocusedBorderColor = AiPalette.Outline.copy(alpha = 0.25f),
        cursorColor = AiPalette.Primary,
        focusedLabelColor = AiPalette.Primary,
        unfocusedLabelColor = onCard.copy(alpha = 0.6f),
        focusedTextColor = onCard,
        unfocusedTextColor = onCard,
        focusedContainerColor = Color.White,
        unfocusedContainerColor = AiPalette.SurfaceLight.copy(alpha = 0.4f)
    )
    StepTitle(stringResource(Res.string.label_injuries))
    Text(
        text = stringResource(Res.string.injuries_hint),
        style = MaterialTheme.typography.bodyMedium,
        color = onCard.copy(alpha = 0.72f)
    )
    // Quick-pick common injuries
    val commonInjuries = listOf(
        stringResource(Res.string.injury_knee),
        stringResource(Res.string.injury_back),
        stringResource(Res.string.injury_shoulder),
        stringResource(Res.string.injury_wrist),
        stringResource(Res.string.injury_elbow),
        stringResource(Res.string.injury_hip),
        stringResource(Res.string.injury_ankle),
        stringResource(Res.string.injury_neck)
    )
    val selectedInjuries = state.injuries.split(",")
        .map { it.trim() }.filter { it.isNotEmpty() }.toSet()
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        commonInjuries.forEach { injury ->
            val selected = injury in selectedInjuries
            ModernToggleChip(
                label = injury,
                selected = selected,
                onClick = {
                    presenter.update { st ->
                        val cur = st.injuries.split(",").map { it.trim() }.filter { it.isNotEmpty() }.toMutableSet()
                        if (selected) cur.remove(injury) else cur.add(injury)
                        st.copy(injuries = cur.joinToString(", "))
                    }
                }
            )
        }
    }
    OutlinedTextField(
        value = state.injuries,
        onValueChange = { presenter.update { st -> st.copy(injuries = it) } },
        label = { Text(stringResource(Res.string.label_injuries_manual)) },
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        colors = inputColors
    )
}
