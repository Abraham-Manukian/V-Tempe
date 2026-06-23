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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.vtempe.core.designsystem.theme.AiPalette
import com.vtempe.shared.domain.exercise.ExerciseCalibrationKind
import com.vtempe.shared.domain.exercise.ExerciseLibrary
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
    onSetDone: (Int?, Double?, Double?) -> Unit,
    onUseSuggestedRest: (Int) -> Unit,
    onAskCoach: (String) -> Unit
) {
    val guide = exerciseGuide(plannedSet.exerciseId, coachTrainerId)
    val exerciseName = exerciseLabel(plannedSet.exerciseId)
    val calibrationKind = ExerciseLibrary.findByIdOrAlias(plannedSet.exerciseId)?.calibrationKind
        ?: ExerciseCalibrationKind.WEIGHT_AND_REPS
    val isBodyweight = calibrationKind == ExerciseCalibrationKind.BODYWEIGHT_REPS
    val isDurationSeconds = calibrationKind == ExerciseCalibrationKind.DURATION_SECONDS
    val isDurationMinutes = calibrationKind == ExerciseCalibrationKind.DURATION_MINUTES
    val isDuration = isDurationSeconds || isDurationMinutes
    val askCoachPrompt = stringResource(Res.string.workout_ask_coach_prompt).kmpFormat(exerciseName)
    val completed = performed?.completed == true
    val totalSets = plannedSet.sets.coerceAtLeast(1)
    val doneSets = performed?.completedSetsCount ?: 0
    val currentSetNumber = (doneSets + 1).coerceAtMost(totalSets)
    val isLastSet = doneSets + 1 >= totalSets

    // Auto-dismiss when all sets of this exercise are done
    LaunchedEffect(completed) {
        if (completed) onDismiss()
    }

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
                .navigationBarsPadding()
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(0.dp)
        ) {
            // ── Exercise image — full-bleed, name overlaid ────────
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(210.dp)
                    .clickable { showImageFullScreen = true }
            ) {
                Image(
                    painter = painterResource(guide.illustration),
                    contentDescription = exerciseName,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
                // Gradient overlay — name + done badge at the bottom
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                colorStops = arrayOf(
                                    0.35f to Color.Transparent,
                                    1.00f to Color.Black.copy(alpha = 0.68f)
                                )
                            )
                        )
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter)
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Bottom
                ) {
                    Text(
                        exerciseName,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        modifier = Modifier.weight(1f)
                    )
                    if (completed) {
                        Surface(
                            shape = CircleShape,
                            color = AiPalette.DeepAccent.copy(alpha = 0.90f)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    Icons.Filled.CheckCircle,
                                    contentDescription = null,
                                    tint = AiPalette.OnDeepAccent,
                                    modifier = Modifier.size(14.dp)
                                )
                                Text(
                                    stringResource(Res.string.workout_status_done),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = AiPalette.OnDeepAccent,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
                // Fullscreen icon top-right
                Surface(
                    modifier = Modifier.align(Alignment.TopEnd).padding(10.dp),
                    shape = CircleShape,
                    color = Color.Black.copy(alpha = 0.40f)
                ) {
                    Icon(
                        Icons.Filled.Fullscreen,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.padding(6.dp).size(16.dp)
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            // ── Set progress: "Подход X из N" + dots ─────────────
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    "Подход $currentSetNumber из $totalSets",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                // Set dots: filled = done, outline = pending
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    repeat(totalSets) { i ->
                        val isDone = i < doneSets
                        val isCurrent = i == doneSets
                        Box(
                            modifier = Modifier
                                .size(if (isCurrent) 14.dp else 10.dp)
                                .background(
                                    color = when {
                                        isDone -> AiPalette.DeepAccent
                                        isCurrent -> AiPalette.DeepAccent.copy(alpha = 0.35f)
                                        else -> MaterialTheme.colorScheme.surfaceVariant
                                    },
                                    shape = CircleShape
                                )
                        )
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            // ── Target row ────────────────────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    stringResource(Res.string.workout_target_set),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Surface(
                    shape = MaterialTheme.shapes.small,
                    color = AiPalette.DeepAccent.copy(alpha = 0.10f)
                ) {
                    Text(
                        plannedSetSummary(plannedSet),
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = AiPalette.DeepAccent
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            // ── Stepper controls ──────────────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val repsLabel = when {
                    isDurationSeconds -> stringResource(Res.string.workout_seconds_label)
                    isDurationMinutes -> stringResource(Res.string.workout_minutes_label)
                    else -> stringResource(Res.string.workout_reps_label)
                }
                val repsStep = if (isDurationSeconds) 5 else 1
                val repsMax = if (isDurationSeconds) 600 else if (isDurationMinutes) 60 else 99
                val repsMin = if (isDuration) 5 else 1
                StepperControl(
                    modifier = Modifier.weight(if (isDuration) 1.4f else 1f),
                    label = repsLabel,
                    value = "$reps",
                    onDecrement = { if (reps > repsMin) reps -= repsStep },
                    onIncrement = { if (reps < repsMax) reps += repsStep }
                )
                if (!isDuration) {
                    StepperControl(
                        modifier = Modifier.weight(1.4f),
                        label = stringResource(
                            if (isBodyweight) Res.string.workout_weight_extra_label
                            else Res.string.workout_weight_label
                        ),
                        value = weight?.toEditableDecimal() ?: "—",
                        onDecrement = {
                            weight = ((weight ?: 0.0) - 2.5).let { if (it <= 0.0) null else it }
                        },
                        onIncrement = { weight = (weight ?: 0.0) + 2.5 }
                    )
                }
                StepperControl(
                    modifier = Modifier.weight(1f),
                    label = stringResource(Res.string.workout_rpe_label),
                    subtitle = stringResource(Res.string.workout_rpe_scale),
                    value = rpe?.toEditableDecimal() ?: "—",
                    onDecrement = { rpe = ((rpe ?: 6.0) - 0.5).coerceIn(1.0, 10.0) },
                    onIncrement = { rpe = ((rpe ?: 5.5) + 0.5).coerceIn(1.0, 10.0) }
                )
            }

            // ── Calibration hint: shown when set not yet done and weight is planned ──
            if (!completed && performed?.actualWeightKg == null && plannedSet.weightKg != null && !isBodyweight && !isDuration) {
                Spacer(Modifier.height(4.dp))
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp),
                    shape = MaterialTheme.shapes.small,
                    color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            Icons.Filled.FitnessCenter,
                            contentDescription = null,
                            modifier = Modifier.size(13.dp),
                            tint = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                        Text(
                            stringResource(Res.string.workout_calibration_hint),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            // ── Primary CTA: mark set done + start rest ──────────
            Button(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp)
                    .padding(horizontal = 20.dp),
                onClick = { onSetDone(reps, weight, rpe) },
                enabled = !completed,
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isLastSet) MaterialTheme.colorScheme.tertiary
                    else AiPalette.DeepAccent,
                    contentColor = AiPalette.OnDeepAccent
                ),
                shape = MaterialTheme.shapes.large
            ) {
                Icon(
                    Icons.Filled.CheckCircle,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    if (isLastSet) "Последний подход · Готово!"
                    else "Подход выполнен · Отдых",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                if (!isLastSet) {
                    Spacer(Modifier.width(6.dp))
                    Icon(Icons.Filled.Timer, contentDescription = null, modifier = Modifier.size(14.dp))
                }
            }

            // Secondary: mark not done (undo)
            if (doneSets > 0) {
                TextButton(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp),
                    onClick = {
                        onResultChanged(false, reps, weight, rpe)
                    }
                ) {
                    Text(
                        stringResource(Res.string.workout_mark_not_done),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            HorizontalDivider(
                modifier = Modifier.padding(horizontal = 20.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            )

            Spacer(Modifier.height(4.dp))

            // ── Collapsibles ──────────────────────────────────────
            Column(
                modifier = Modifier.padding(horizontal = 20.dp),
                verticalArrangement = Arrangement.spacedBy(0.dp)
            ) {
                // Technique
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

                // Rest presets
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

                // Ask coach
                OutlinedButton(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = { onAskCoach(askCoachPrompt) }
                ) {
                    Icon(Icons.Filled.FitnessCenter, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(Res.string.workout_ask_coach))
                }
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
