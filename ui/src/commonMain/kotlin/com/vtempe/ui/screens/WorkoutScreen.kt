@file:OptIn(org.jetbrains.compose.resources.ExperimentalResourceApi::class, androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.vtempe.ui.screens
import com.vtempe.ui.*

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DirectionsBike
import androidx.compose.material.icons.filled.DirectionsRun
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.SelfImprovement
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.vtempe.core.designsystem.components.BrandScreen
import com.vtempe.core.designsystem.theme.AiPalette
import com.vtempe.shared.domain.model.Workout
import com.vtempe.shared.domain.model.WorkoutSet
import com.vtempe.ui.util.kmpFormat
import com.vtempe.ui.LocalBottomBarHeight
import com.vtempe.ui.LocalTopBarHeight
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import kotlin.math.roundToInt

@Composable
fun WorkoutScreen(
    presenter: WorkoutPresenter = rememberWorkoutPresenter()
) {
    val state by presenter.state.collectAsState()
    val showAddSheet = remember { mutableStateOf(false) }
    
    val topBarHeight = LocalTopBarHeight.current
    val bottomBarHeight = LocalBottomBarHeight.current

    BrandScreen(Modifier.fillMaxSize()) {
        Scaffold(
            containerColor = Color.Transparent,
            floatingActionButton = {
                FloatingActionButton(
                    modifier = Modifier.padding(bottom = bottomBarHeight),
                    onClick = { showAddSheet.value = true }
                ) {
                    Icon(Icons.Filled.Add, contentDescription = stringResource(Res.string.workout_add_button))
                }
            }
        ) { padding ->
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    top = topBarHeight + 16.dp,
                    bottom = bottomBarHeight + 80.dp, // РћСЃС‚Р°РІР»СЏРµРј РјРµСЃС‚Рѕ РїРѕРґ FAB Рё РѕС‚СЃС‚СѓРї
                    start = 20.dp,
                    end = 20.dp
                ),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                items(state.workouts) { workout ->
                    val feedback = state.feedback[workout.id] ?: WorkoutFeedback()
                    val selected = state.selectedWorkoutId == workout.id
                    AnimatedVisibility(
                        visible = true,
                        enter = fadeIn(animationSpec = tween(300)) + slideInVertically(
                            initialOffsetY = { it / 5 },
                            animationSpec = tween(300)
                        )
                    ) {
                        WorkoutCard(
                            workout = workout,
                            feedback = feedback,
                            selected = selected,
                            onSelect = { presenter.select(workout.id) },
                            onToggle = { index, completed ->
                                presenter.toggleSetCompleted(workout.id, index, completed)
                            },
                            onUpdateNotes = { presenter.updateNotes(workout.id, it) },
                            onSubmit = { presenter.submitFeedback(workout.id) }
                        )
                    }
                }
            }
        }
        if (showAddSheet.value) {
            AddSetSheet(
                onDismiss = { showAddSheet.value = false },
                onAdd = { exercise, reps, weight ->
                    presenter.addSet(exercise, reps, weight)
                    showAddSheet.value = false
                }
            )
        }
    }
}

@Composable
private fun WorkoutCard(
    workout: Workout,
    feedback: WorkoutFeedback,
    selected: Boolean,
    onSelect: () -> Unit,
    onToggle: (Int, Boolean) -> Unit,
    onUpdateNotes: (String) -> Unit,
    onSubmit: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onSelect),
        colors = workoutCardColors(),
        elevation = workoutCardElevation(),
        shape = MaterialTheme.shapes.large,
        border = if (selected) BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)) else null
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            val leadExerciseId = workout.sets.firstOrNull()?.exerciseId ?: "Session"
            val leadExercise = exerciseLabel(leadExerciseId)
            val dateLabel = workout.date.toString()
            val progress =
                if (workout.sets.isNotEmpty()) feedback.completedSets.size / workout.sets.size.toFloat() else 0f
            WorkoutCardHeader(
                title = leadExercise,
                date = dateLabel,
                setsCount = workout.sets.size,
                completed = feedback.completedSets.size,
                progress = progress
            )
            LinearProgressIndicator(
                progress = progress,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(CircleShape),
                trackColor = MaterialTheme.colorScheme.surfaceVariant,
                color = MaterialTheme.colorScheme.primary
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
            workout.sets.forEachIndexed { index, set ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier.width(48.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Checkbox(
                            checked = feedback.completedSets.contains(index),
                            onCheckedChange = { onToggle(index, it) }
                        )
                    }
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(start = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        val exerciseName = exerciseLabel(set.exerciseId)
                        Text(
                            exerciseName,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        val weightLabel = set.weightKg?.let {
                            stringResource(Res.string.workout_weight_display).kmpFormat(it)
                        } ?: stringResource(Res.string.workout_weight_bodyweight)
                        Text(
                            stringResource(Res.string.workout_set_summary).kmpFormat(
                                set.reps,
                                weightLabel
                            ),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            if (selected) {
                Text(
                    stringResource(Res.string.workout_feedback_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                OutlinedTextField(
                    value = feedback.notes,
                    onValueChange = onUpdateNotes,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(Res.string.workout_notes_label)) },
                    placeholder = { Text(stringResource(Res.string.workout_notes_hint)) },
                    shape = MaterialTheme.shapes.medium
                )
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(
                        onClick = onSubmit,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = AiPalette.DeepAccent,
                            contentColor = AiPalette.OnDeepAccent
                        ),
                        modifier = Modifier.weight(1f)
                    ) { Text(stringResource(Res.string.workout_mark_complete)) }
                    OutlinedButton(
                        onClick = { onUpdateNotes("") },
                        modifier = Modifier.weight(1f)
                    ) { Text(stringResource(Res.string.action_refresh)) }
                }
            }
        }
    }
}

@Composable
private fun WorkoutCardHeader(
    title: String,
    date: String,
    setsCount: Int,
    completed: Int,
    progress: Float
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(date, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text(
            stringResource(Res.string.workout_progress_status).kmpFormat(
                (progress * 100).roundToInt(),
                completed,
                setsCount
            ),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary
        )
    }
}

@Composable
private fun AddSetSheet(
    onDismiss: () -> Unit,
    onAdd: (String, Int, Double?) -> Unit
) {
    val scope = rememberCoroutineScope()
    val exercise = remember { mutableStateOf("") }
    val reps = remember { mutableStateOf("10") }
    val weight = remember { mutableStateOf("20") }
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = MaterialTheme.colorScheme.surface) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(stringResource(Res.string.workout_add_set_title), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            OutlinedTextField(
                value = exercise.value,
                onValueChange = { exercise.value = it },
                label = { Text(stringResource(Res.string.workout_exercise_label)) },
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = reps.value,
                onValueChange = { reps.value = it.filter { c -> c.isDigit() }.take(3) },
                label = { Text(stringResource(Res.string.workout_reps_label)) },
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = weight.value,
                onValueChange = { weight.value = it.filter { c -> c.isDigit() || c == '.' }.take(5) },
                label = { Text(stringResource(Res.string.workout_weight_label)) },
                modifier = Modifier.fillMaxWidth()
            )
            FilledTonalButton(onClick = {
                val repsInt = reps.value.toIntOrNull() ?: 0
                val weightVal = weight.value.toDoubleOrNull()
                onAdd(exercise.value.ifBlank { "custom" }, repsInt, weightVal)
                scope.launch { onDismiss() }
            }) { Text(stringResource(Res.string.workout_add_button)) }
        }
    }
}

@Composable
private fun exerciseLabel(exerciseId: String): String {
    val normalized = exerciseId.trim().lowercase().replace('-', '_').replace(' ', '_')
    val resource = when (normalized) {
        "squat", "back_squat" -> Res.string.workout_exercise_squat
        "bench", "bench_press" -> Res.string.workout_exercise_bench
        "deadlift" -> Res.string.workout_exercise_deadlift
        "ohp" -> Res.string.workout_exercise_ohp
        "row", "bent_over_row", "barbell_row" -> Res.string.workout_exercise_row
        "pullup", "pull_up", "pullups" -> Res.string.workout_exercise_pullup
        "lunge", "walking_lunge" -> Res.string.workout_exercise_lunge
        "dip", "parallel_bar_dip", "parallel_bar_dips" -> Res.string.workout_exercise_dip
        "pushup", "push_up" -> Res.string.workout_exercise_pushup
        "curl", "bicep_curl", "biceps_curl" -> Res.string.workout_exercise_curl
        "tricep_extension", "triceps_extension", "triceps_extensions" -> Res.string.workout_exercise_tricep_extension
        "plank", "plank_hold" -> Res.string.workout_exercise_plank
        "hip_thrust", "hipthrust" -> Res.string.workout_exercise_hip_thrust
        "leg_press", "legpress" -> Res.string.workout_exercise_leg_press
        "run", "running" -> Res.string.workout_exercise_run
        "bike", "cycling" -> Res.string.workout_exercise_bike
        "yoga" -> Res.string.workout_exercise_yoga
        else -> null
    }
    return resource?.let { stringResource(it) }
        ?: exerciseId.replace('_', ' ').replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
}

@Composable
private fun workoutCardColors() =
    CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.97f))

@Composable
private fun workoutCardElevation() =
    CardDefaults.cardElevation(defaultElevation = 8.dp)
