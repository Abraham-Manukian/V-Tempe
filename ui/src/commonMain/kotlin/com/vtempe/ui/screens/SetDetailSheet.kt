@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.vtempe.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.vtempe.core.designsystem.theme.AiPalette
import com.vtempe.shared.domain.model.PerformedSet
import com.vtempe.shared.domain.model.WorkoutSet
import com.vtempe.ui.Res
import com.vtempe.ui.*
import com.vtempe.ui.util.kmpFormat
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun SetDetailSheet(
    setIndex: Int,
    plannedSet: WorkoutSet,
    performed: PerformedSet?,
    coachTrainerId: String,
    restSeconds: Int,
    onDismiss: () -> Unit,
    onResultChanged: (Boolean, Int?, Double?, Double?) -> Unit,
    onDoneStartRest: (Int?, Double?, Double?) -> Unit,
    onUseSuggestedRest: (Int) -> Unit,
    onAskCoach: (String) -> Unit
) {
    val guide = exerciseGuide(plannedSet.exerciseId, coachTrainerId)
    val exerciseName = exerciseLabel(plannedSet.exerciseId)
    val askCoachPrompt = stringResource(Res.string.workout_ask_coach_prompt).kmpFormat(exerciseName)
    val completed = performed?.completed == true

    var reps by remember(setIndex, performed?.actualReps, plannedSet.reps) {
        mutableIntStateOf(performed?.actualReps ?: plannedSet.reps)
    }
    var weight by remember(setIndex, performed?.actualWeightKg, plannedSet.weightKg) {
        mutableStateOf(performed?.actualWeightKg ?: plannedSet.weightKg)
    }
    var rpe by remember(setIndex, performed?.actualRpe, plannedSet.rpe) {
        mutableStateOf(performed?.actualRpe ?: plannedSet.rpe)
    }

    var showTechnique by remember { mutableStateOf(false) }
    var showRestPresets by remember { mutableStateOf(false) }
    var showImageFullScreen by remember { mutableStateOf(false) }

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Title row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    exerciseName,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                if (completed) {
                    StatusPill(stringResource(Res.string.workout_status_done), AiPalette.DeepAccent)
                }
            }

            // Exercise illustration — tap to expand fullscreen
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(240.dp)
                    .clip(MaterialTheme.shapes.large)
                    .clickable { showImageFullScreen = true }
            ) {
                Image(
                    painter = painterResource(guide.illustration),
                    contentDescription = exerciseName,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
                // Dark gradient at bottom for readability of fullscreen button
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(64.dp)
                        .align(Alignment.BottomCenter)
                        .background(
                            androidx.compose.ui.graphics.Brush.verticalGradient(
                                listOf(Color.Transparent, Color.Black.copy(alpha = 0.45f))
                            )
                        )
                )
                Surface(
                    modifier = Modifier.align(Alignment.BottomEnd).padding(10.dp),
                    shape = CircleShape,
                    color = Color.Black.copy(alpha = 0.55f)
                ) {
                    Icon(
                        Icons.Filled.Fullscreen,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.padding(6.dp).size(18.dp)
                    )
                }
            }

            // Target pill
            Surface(
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        stringResource(Res.string.workout_target_set),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        plannedSetSummary(plannedSet),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            // ── Stepper controls ──────────────────────────────────
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
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
                    onDecrement = {
                        weight = ((weight ?: 0.0) - 2.5).let { if (it <= 0.0) null else it }
                    },
                    onIncrement = { weight = (weight ?: 0.0) + 2.5 }
                )
                StepperControl(
                    modifier = Modifier.weight(1f),
                    label = stringResource(Res.string.workout_rpe_label),
                    subtitle = stringResource(Res.string.workout_rpe_scale),
                    value = rpe?.toEditableDecimal() ?: "—",
                    onDecrement = { rpe = ((rpe ?: 6.0) - 0.5).coerceIn(1.0, 10.0) },
                    onIncrement = { rpe = ((rpe ?: 5.5) + 0.5).coerceIn(1.0, 10.0) }
                )
            }

            // ── Primary CTA: mark done + start rest ──────────────
            Button(
                modifier = Modifier.fillMaxWidth().height(52.dp),
                onClick = { onDoneStartRest(reps, weight, rpe) },
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (completed) MaterialTheme.colorScheme.surfaceVariant
                    else AiPalette.DeepAccent,
                    contentColor = if (completed) MaterialTheme.colorScheme.onSurfaceVariant
                    else AiPalette.OnDeepAccent
                ),
                shape = MaterialTheme.shapes.large
            ) {
                Icon(
                    if (completed) Icons.Filled.RadioButtonUnchecked else Icons.Filled.CheckCircle,
                    contentDescription = null
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    if (completed) stringResource(Res.string.workout_mark_not_done)
                    else stringResource(Res.string.workout_done_start_rest),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                if (!completed) {
                    Spacer(Modifier.width(6.dp))
                    Icon(Icons.Filled.Timer, contentDescription = null, modifier = Modifier.size(16.dp))
                }
            }

            // Save without starting rest (secondary, only when not done)
            if (!completed) {
                OutlinedButton(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = {
                        onResultChanged(true, reps, weight, rpe)
                        onDismiss()
                    }
                ) {
                    Text(stringResource(Res.string.workout_save_result))
                }
            }

            HorizontalDivider(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            )

            // ── Collapsible: Technique ────────────────────────────
            CollapsibleSection(
                title = stringResource(Res.string.workout_how_to_perform),
                expanded = showTechnique,
                onToggle = { showTechnique = !showTechnique }
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    guide.steps.forEachIndexed { i, step ->
                        Text(
                            stringResource(Res.string.workout_step_line).kmpFormat(i + 1, step),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                    Surface(
                        shape = MaterialTheme.shapes.medium,
                        color = AiPalette.DeepAccent.copy(alpha = 0.08f)
                    ) {
                        Text(
                            guide.cue,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            style = MaterialTheme.typography.bodyMedium,
                            color = AiPalette.DeepAccent,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }

            // ── Collapsible: Rest presets ─────────────────────────
            CollapsibleSection(
                title = "${stringResource(Res.string.workout_suggested_rest)}: ${guide.defaultRestSeconds}s",
                expanded = showRestPresets,
                onToggle = { showRestPresets = !showRestPresets }
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(60, 90, 120, 180).forEach { preset ->
                        OutlinedButton(
                            onClick = { onUseSuggestedRest(preset) },
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                            colors = ButtonDefaults.outlinedButtonColors(
                                containerColor = if (preset == restSeconds)
                                    AiPalette.DeepAccent.copy(alpha = 0.12f)
                                else Color.Transparent
                            )
                        ) {
                            Text(stringResource(Res.string.workout_rest_seconds_compact).kmpFormat(preset))
                        }
                    }
                }
            }

            // ── Ask coach ─────────────────────────────────────────
            OutlinedButton(
                modifier = Modifier.fillMaxWidth(),
                onClick = { onAskCoach(askCoachPrompt) }
            ) {
                Icon(Icons.Filled.FitnessCenter, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(Res.string.workout_ask_coach))
            }
        }
    }

    if (showImageFullScreen) {
        ExerciseImageDialog(
            illustration = guide.illustration,
            exerciseName = exerciseName,
            cue = guide.cue,
            onDismiss = { showImageFullScreen = false }
        )
    }
}
