@file:OptIn(
    org.jetbrains.compose.resources.ExperimentalResourceApi::class,
    androidx.compose.foundation.layout.ExperimentalLayoutApi::class,
    androidx.compose.material3.ExperimentalMaterial3Api::class
)

package com.vtempe.ui.screens.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.vtempe.core.designsystem.theme.AiPalette
import com.vtempe.shared.domain.model.Sex
import com.vtempe.ui.*
import com.vtempe.ui.presenter.OnboardingPresenter
import com.vtempe.ui.presenter.OnboardingState
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun BodyMeasurementsStep(state: OnboardingState, presenter: OnboardingPresenter) {
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
    StepTitle(stringResource(Res.string.settings_title))
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedTextField(
            value = state.age,
            onValueChange = { v -> presenter.update { st -> st.copy(age = v.filter { it.isDigit() }.take(2)) } },
            label = { Text(stringResource(Res.string.label_age)) },
            modifier = Modifier.weight(1f),
            shape = MaterialTheme.shapes.medium,
            colors = inputColors
        )
        OutlinedTextField(
            value = state.heightCm,
            onValueChange = { v -> presenter.update { st -> st.copy(heightCm = v.filter { it.isDigit() }.take(3)) } },
            label = { Text(stringResource(Res.string.label_height_cm)) },
            modifier = Modifier.weight(1f),
            shape = MaterialTheme.shapes.medium,
            colors = inputColors
        )
        OutlinedTextField(
            value = state.weightKg,
            onValueChange = { v -> presenter.update { st -> st.copy(weightKg = v.filter { it.isDigit() || it == '.' }.take(5)) } },
            label = { Text(stringResource(Res.string.label_weight_kg)) },
            modifier = Modifier.weight(1f),
            shape = MaterialTheme.shapes.medium,
            colors = inputColors
        )
    }

    Text(
        stringResource(Res.string.label_sex),
        style = MaterialTheme.typography.titleSmall,
        color = onCard
    )
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Sex.entries.forEach { option ->
            val label = when (option) {
                Sex.MALE -> stringResource(Res.string.sex_male)
                Sex.FEMALE -> stringResource(Res.string.sex_female)
                Sex.OTHER -> stringResource(Res.string.sex_other)
            }
            ModernChip(
                selected = state.sex == option,
                label = label,
                onClick = { presenter.update { it.copy(sex = option) } }
            )
        }
    }
}
