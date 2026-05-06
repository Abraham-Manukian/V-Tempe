package com.vtempe.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.vtempe.core.designsystem.theme.AiPalette
import com.vtempe.shared.domain.model.Workout
import com.vtempe.shared.domain.model.WorkoutProgress
import com.vtempe.ui.Res
import com.vtempe.ui.*
import com.vtempe.ui.util.kmpFormat
import org.jetbrains.compose.resources.stringResource
import kotlin.math.roundToInt

@Composable
internal fun WorkoutListContent(
    state: WorkoutState,
    topBarHeight: Dp,
    bottomBarHeight: Dp,
    onOpenWorkout: (String) -> Unit
) {
    Scaffold(containerColor = Color.Transparent) { _ ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                top = topBarHeight + 16.dp,
                bottom = bottomBarHeight + 28.dp,
                start = 20.dp,
                end = 20.dp
            ),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            if (state.workouts.isEmpty()) {
                item { WorkoutEmptyState() }
            } else {
                item {
                    WorkoutListHeader(
                        onStartWorkout = { onOpenWorkout(state.workouts.first().id) }
                    )
                }
                items(state.workouts, key = { it.id }) { workout ->
                    WorkoutOverviewCard(
                        workout = workout,
                        progress = state.progress[workout.id] ?: WorkoutProgress(workoutId = workout.id),
                        selected = state.selectedWorkoutId == workout.id,
                        onOpen = { onOpenWorkout(workout.id) }
                    )
                }
            }
        }
    }
}

@Composable
internal fun WorkoutListHeader(onStartWorkout: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = workoutCardColors(),
        elevation = workoutCardElevation()
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                stringResource(Res.string.workout_plan_title),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            Button(
                modifier = Modifier.fillMaxWidth(),
                onClick = onStartWorkout,
                colors = ButtonDefaults.buttonColors(
                    containerColor = AiPalette.DeepAccent,
                    contentColor = AiPalette.OnDeepAccent
                )
            ) {
                Icon(Icons.Filled.PlayArrow, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(Res.string.workout_start_first))
            }
        }
    }
}

@Composable
internal fun WorkoutEmptyState() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = workoutCardColors(),
        elevation = workoutCardElevation()
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                stringResource(Res.string.workout_empty_title),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            Text(
                stringResource(Res.string.workout_empty_body),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
internal fun WorkoutOverviewCard(
    workout: Workout,
    progress: WorkoutProgress,
    selected: Boolean,
    onOpen: () -> Unit
) {
    val completedCount = progress.performedSets.count { it.completed }
    val progressValue = if (workout.sets.isEmpty()) 0f
    else completedCount.toFloat() / workout.sets.size.toFloat()

    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onOpen),
        colors = workoutCardColors(),
        elevation = workoutCardElevation(),
        border = if (selected) BorderStroke(1.5.dp, AiPalette.DeepAccent.copy(alpha = 0.45f)) else null,
        shape = MaterialTheme.shapes.large
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        exerciseLabel(workout.sets.firstOrNull()?.exerciseId ?: "workout"),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        workout.date.toString(),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (progressValue >= 1f) {
                    StatusPill(stringResource(Res.string.workout_status_done), AiPalette.DeepAccent)
                } else if (completedCount > 0) {
                    Text(
                        stringResource(Res.string.workout_progress_status)
                            .kmpFormat((progressValue * 100).roundToInt(), completedCount, workout.sets.size),
                        style = MaterialTheme.typography.labelMedium,
                        color = AiPalette.DeepAccent,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            // Thin progress bar
            LinearProgressIndicator(
                progress = { progressValue },
                modifier = Modifier.fillMaxWidth().height(6.dp).clip(CircleShape),
                trackColor = MaterialTheme.colorScheme.surfaceVariant,
                color = AiPalette.DeepAccent
            )

            // Compact exercise preview rows
            workout.sets.take(4).forEachIndexed { index, set ->
                val done = progress.performedSets.any { it.setIndex == index && it.completed }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        imageVector = if (done) Icons.Filled.CheckCircle else Icons.Filled.RadioButtonUnchecked,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = if (done) AiPalette.DeepAccent
                        else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                    )
                    Text(
                        exerciseLabel(set.exerciseId),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = if (done) AiPalette.DeepAccent else MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        plannedSetSummary(set),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            if (workout.sets.size > 4) {
                Text(
                    stringResource(Res.string.workout_more_exercises).kmpFormat(workout.sets.size - 4),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Button(
                modifier = Modifier.fillMaxWidth(),
                onClick = onOpen,
                colors = ButtonDefaults.buttonColors(
                    containerColor = AiPalette.DeepAccent,
                    contentColor = AiPalette.OnDeepAccent
                )
            ) {
                Icon(Icons.Filled.PlayArrow, contentDescription = null)
                Spacer(Modifier.width(6.dp))
                Text(
                    if (completedCount == 0) stringResource(Res.string.workout_start)
                    else stringResource(Res.string.workout_open_details)
                )
            }
        }
    }
}
