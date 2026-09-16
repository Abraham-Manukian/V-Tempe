@file:OptIn(
    org.jetbrains.compose.resources.ExperimentalResourceApi::class,
    androidx.compose.foundation.layout.ExperimentalLayoutApi::class,
    androidx.compose.material3.ExperimentalMaterial3Api::class
)

package com.vtempe.ui.screens.onboarding

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.vtempe.core.designsystem.theme.AiPalette
import com.vtempe.ui.*
import com.vtempe.ui.presenter.OnboardingPresenter
import com.vtempe.ui.presenter.OnboardingState
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun DietStep(state: OnboardingState, presenter: OnboardingPresenter) {
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
    StepTitle(stringResource(Res.string.label_dietary_prefs))
    OutlinedTextField(
        value = state.dietaryPreferences,
        onValueChange = { presenter.update { st -> st.copy(dietaryPreferences = it) } },
        label = { Text(stringResource(Res.string.label_dietary_prefs)) },
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        colors = inputColors
    )
    OutlinedTextField(
        value = state.allergies,
        onValueChange = { presenter.update { st -> st.copy(allergies = it) } },
        label = { Text(stringResource(Res.string.label_allergies)) },
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        colors = inputColors
    )
}
