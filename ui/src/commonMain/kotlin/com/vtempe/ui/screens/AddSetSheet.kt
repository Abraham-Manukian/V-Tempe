@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.vtempe.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.vtempe.core.designsystem.theme.AiPalette
import com.vtempe.ui.Res
import com.vtempe.ui.*
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun AddSetSheet(
    onDismiss: () -> Unit,
    onAdd: (String, Int, Double?, Double?) -> Unit
) {
    var exercise by remember { mutableStateOf("") }
    var reps by remember { mutableIntStateOf(10) }
    var weight by remember { mutableStateOf<Double?>(null) }
    var rpe by remember { mutableStateOf<Double?>(null) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                stringResource(Res.string.workout_add_set_title),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            OutlinedTextField(
                modifier = Modifier.fillMaxWidth(),
                value = exercise,
                onValueChange = { exercise = it },
                label = { Text(stringResource(Res.string.workout_exercise_label)) },
                singleLine = true
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StepperControl(
                    modifier = Modifier.weight(1f),
                    label = stringResource(Res.string.workout_reps_label),
                    value = "$reps",
                    onDecrement = { if (reps > 1) reps-- },
                    onIncrement = { if (reps < 99) reps++ }
                )
                StepperControl(
                    modifier = Modifier.weight(1.5f),
                    label = stringResource(Res.string.workout_weight_label),
                    value = weight?.toEditableDecimal() ?: "—",
                    onDecrement = { weight = ((weight ?: 0.0) - 2.5).let { if (it <= 0.0) null else it } },
                    onIncrement = { weight = (weight ?: 0.0) + 2.5 }
                )
                StepperControl(
                    modifier = Modifier.weight(1f),
                    label = stringResource(Res.string.workout_rpe_label),
                    value = rpe?.toEditableDecimal() ?: "—",
                    onDecrement = { rpe = ((rpe ?: 6.0) - 0.5).coerceIn(1.0, 10.0) },
                    onIncrement = { rpe = ((rpe ?: 5.5) + 0.5).coerceIn(1.0, 10.0) }
                )
            }
            Button(
                modifier = Modifier.fillMaxWidth(),
                onClick = { onAdd(exercise.ifBlank { "custom" }, reps, weight, rpe) },
                colors = ButtonDefaults.buttonColors(
                    containerColor = AiPalette.DeepAccent,
                    contentColor = AiPalette.OnDeepAccent
                )
            ) {
                Text(stringResource(Res.string.workout_add_button))
            }
        }
    }
}
