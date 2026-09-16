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
internal fun EquipmentStep(state: OnboardingState, presenter: OnboardingPresenter) {
    val equipmentOptions = listOf(
        stringResource(Res.string.equipment_dumbbells),
        stringResource(Res.string.equipment_barbell),
        stringResource(Res.string.equipment_kettlebell),
        stringResource(Res.string.equipment_bands),
        stringResource(Res.string.equipment_bench),
        stringResource(Res.string.equipment_pullup_bar),
        stringResource(Res.string.equipment_trx),
        stringResource(Res.string.equipment_mat),
        stringResource(Res.string.equipment_cardio)
    )
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
    StepTitle(stringResource(Res.string.label_equipment_presets))
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        equipmentOptions.forEach { option ->
            ModernToggleChip(
                label = option,
                selected = state.selectedEquipment.contains(option),
                onClick = { presenter.toggleEquipment(option) }
            )
        }
    }
    OutlinedTextField(
        value = state.customEquipment,
        onValueChange = { presenter.setCustomEquipment(it) },
        label = { Text(stringResource(Res.string.label_equipment_manual)) },
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        colors = inputColors
    )
}
