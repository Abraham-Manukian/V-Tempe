@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.vtempe.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.vtempe.core.designsystem.theme.AiPalette
import com.vtempe.shared.domain.model.ExtraWorkoutSet
import com.vtempe.shared.domain.model.PerformedSet
import com.vtempe.shared.domain.model.Workout
import com.vtempe.shared.domain.model.WorkoutProgress
import com.vtempe.shared.domain.model.WorkoutSet
import com.vtempe.ui.GlassTopBarContainer
import com.vtempe.ui.Res
import com.vtempe.ui.*
import com.vtempe.ui.util.kmpFormat
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun ActiveWorkoutScreen(
    workout: Workout,
    progress: WorkoutProgress,
    coachTrainerId: String,
    topBarHeight: Dp,
    bottomBarHeight: Dp,
    onBack: () -> Unit,
    onAddSet: (String, Int, Double?, Double?) -> Unit,
    onResultChanged: (Int, Boolean, Int?, Double?, Double?) -> Unit,
    onNotesChanged: (String) -> Unit,
    onRestSecondsChanged: (Int) -> Unit,
    onSubmit: () -> Unit,
    onAskCoach: (String) -> Unit
) {
    var sessionSeconds by remember(workout.id) { mutableIntStateOf(0) }
    var restRemaining by remember(workout.id) { mutableIntStateOf(0) }
    var isRestRunning by remember(workout.id) { mutableStateOf(false) }
    var selectedSetIndex by remember { mutableStateOf<Int?>(null) }
    var showAddSheet by remember { mutableStateOf(false) }

    // Session timer — counts up every second
    LaunchedEffect(workout.id) {
        while (true) {
            delay(1_000)
            sessionSeconds += 1
        }
    }

    // Rest countdown
    LaunchedEffect(isRestRunning) {
        if (!isRestRunning) return@LaunchedEffect
        while (isRestRunning && restRemaining > 0) {
            delay(1_000)
            restRemaining -= 1
        }
        if (restRemaining <= 0) isRestRunning = false
    }

    val completedCount = progress.performedSets.count { it.completed }
    val progressFraction = if (workout.sets.isEmpty()) 0f
    else completedCount.toFloat() / workout.sets.size.toFloat()

    Scaffold(containerColor = Color.Transparent) { _ ->
        Column(modifier = Modifier.fillMaxSize()) {

            // ── Fixed header with progress bar ────────────────────
            ActiveWorkoutHeader(
                onBack = onBack,
                title = exerciseLabel(workout.sets.firstOrNull()?.exerciseId ?: "workout"),
                date = workout.date.toString(),
                sessionSeconds = sessionSeconds,
                completedCount = completedCount,
                totalCount = workout.sets.size,
                progressFraction = progressFraction
            )

            // ── Rest timer banner (animated, only when active) ────
            AnimatedVisibility(
                visible = isRestRunning,
                enter = expandVertically(),
                exit = shrinkVertically()
            ) {
                RestBanner(
                    remainingSeconds = restRemaining,
                    totalSeconds = progress.restSeconds,
                    onSkip = { isRestRunning = false; restRemaining = 0 }
                )
            }

            // ── Scrollable exercise list ──────────────────────────
            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(
                    top = 14.dp,
                    bottom = bottomBarHeight + 32.dp,
                    start = 16.dp,
                    end = 16.dp
                ),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                itemsIndexed(
                    workout.sets,
                    key = { i, s -> "${workout.id}-$i-${s.exerciseId}" }
                ) { index, set ->
                    ExerciseRow(
                        index = index,
                        set = set,
                        performed = progress.performedSets.firstOrNull { it.setIndex == index },
                        onClick = { selectedSetIndex = index }
                    )
                }

                if (progress.extraSets.isNotEmpty()) {
                    item { ExtraSetsRow(progress.extraSets) }
                }

                item {
                    OutlinedButton(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = { showAddSheet = true }
                    ) {
                        Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(Res.string.workout_add_set_title))
                    }
                }

                item {
                    WorkoutNotesCard(
                        progress = progress,
                        onNotes = onNotesChanged,
                        onSubmit = onSubmit
                    )
                }
            }
        }
    }

    // ── Set detail bottom sheet ───────────────────────────────
    val selectedSet = selectedSetIndex?.let { workout.sets.getOrNull(it) }
    if (selectedSet != null && selectedSetIndex != null) {
        val idx = selectedSetIndex!!
        SetDetailSheet(
            setIndex = idx,
            plannedSet = selectedSet,
            performed = progress.performedSets.firstOrNull { it.setIndex == idx },
            coachTrainerId = coachTrainerId,
            restSeconds = progress.restSeconds,
            onDismiss = { selectedSetIndex = null },
            onResultChanged = { completed, reps, weight, rpe ->
                onResultChanged(idx, completed, reps, weight, rpe)
            },
            onDoneStartRest = { reps, weight, rpe ->
                onResultChanged(idx, true, reps, weight, rpe)
                restRemaining = progress.restSeconds
                isRestRunning = true
                selectedSetIndex = null
            },
            onUseSuggestedRest = { seconds ->
                onRestSecondsChanged(seconds)
            },
            onAskCoach = onAskCoach
        )
    }

    if (showAddSheet) {
        AddSetSheet(
            onDismiss = { showAddSheet = false },
            onAdd = { exerciseId, reps, weight, rpe ->
                onAddSet(exerciseId, reps, weight, rpe)
                showAddSheet = false
            }
        )
    }
}

@Composable
internal fun ActiveWorkoutHeader(
    onBack: () -> Unit,
    title: String,
    date: String,
    sessionSeconds: Int,
    completedCount: Int,
    totalCount: Int,
    progressFraction: Float
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        GlassTopBarContainer {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack, modifier = Modifier.size(48.dp)) {
                    Icon(
                        Icons.Filled.ArrowBack,
                        contentDescription = stringResource(Res.string.action_back),
                        modifier = Modifier.size(24.dp)
                    )
                }
                Column(
                    modifier = Modifier.weight(1f).padding(start = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(1.dp)
                ) {
                    Text(
                        title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        date,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Surface(
                    shape = MaterialTheme.shapes.small,
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
                ) {
                    Text(
                        formatDuration(sessionSeconds),
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold
                    )
                }
                Spacer(Modifier.width(8.dp))
                Text(
                    "$completedCount/$totalCount",
                    style = MaterialTheme.typography.labelLarge,
                    color = AiPalette.DeepAccent,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.width(12.dp))
            }
        }
        LinearProgressIndicator(
            progress = { progressFraction },
            modifier = Modifier.fillMaxWidth().height(3.dp),
            trackColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
            color = AiPalette.DeepAccent
        )
    }
}

@Composable
internal fun RestBanner(
    remainingSeconds: Int,
    totalSeconds: Int,
    onSkip: () -> Unit
) {
    val fraction = if (totalSeconds <= 0) 0f
    else (remainingSeconds.toFloat() / totalSeconds.toFloat()).coerceIn(0f, 1f)

    val accentColor = when {
        fraction > 0.5f -> AiPalette.DeepAccent
        fraction > 0.25f -> MaterialTheme.colorScheme.tertiary
        else -> MaterialTheme.colorScheme.error
    }

    Surface(color = accentColor.copy(alpha = 0.1f)) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Filled.Timer,
                    contentDescription = null,
                    tint = accentColor,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    stringResource(Res.string.workout_rest_timer_title),
                    style = MaterialTheme.typography.labelLarge,
                    color = accentColor,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    formatDuration(remainingSeconds),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = accentColor
                )
                Spacer(Modifier.width(8.dp))
                TextButton(
                    onClick = onSkip,
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                ) {
                    Text(stringResource(Res.string.workout_rest_skip), color = accentColor)
                }
            }
            LinearProgressIndicator(
                progress = { fraction },
                modifier = Modifier.fillMaxWidth().height(2.dp),
                trackColor = accentColor.copy(alpha = 0.15f),
                color = accentColor
            )
        }
    }
}

@Composable
internal fun ExerciseRow(
    index: Int,
    set: WorkoutSet,
    performed: PerformedSet?,
    onClick: () -> Unit
) {
    val completed = performed?.completed == true

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.large)
            .clickable(onClick = onClick),
        shape = MaterialTheme.shapes.large,
        color = if (completed) Color.White.copy(alpha = 0.78f) else Color.White.copy(alpha = 0.97f),
        tonalElevation = 0.dp,
        shadowElevation = if (completed) 0.dp else 4.dp
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Badge: solid accent circle with checkmark (done) or number (pending)
            if (completed) {
                Icon(
                    Icons.Filled.CheckCircle,
                    contentDescription = null,
                    tint = AiPalette.DeepAccent,
                    modifier = Modifier.size(36.dp)
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .background(AiPalette.DeepAccent.copy(alpha = 0.14f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "${index + 1}",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = AiPalette.DeepAccent
                    )
                }
            }

            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    exerciseLabel(set.exerciseId),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurface.copy(
                        alpha = if (completed) 0.42f else 0.9f
                    )
                )
                val subtitleText = if (performed != null && performed.actualReps != null) {
                    buildPerformedSummary(performed)
                } else {
                    plannedSetSummary(set)
                }
                Text(
                    subtitleText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(
                        alpha = if (completed) 0.38f else 0.68f
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Icon(
                Icons.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(
                    alpha = if (completed) 0.2f else 0.4f
                ),
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
internal fun ExtraSetsRow(extraSets: List<ExtraWorkoutSet>) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.97f),
        tonalElevation = 2.dp
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                stringResource(Res.string.workout_extra_sets_title),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.tertiary
            )
            extraSets.forEach { s ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        Icons.Filled.Add,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.tertiary
                    )
                    Text(
                        exerciseLabel(s.exerciseId),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        plannedSetSummary(WorkoutSet(s.exerciseId, s.reps, s.weightKg, s.rpe)),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
internal fun WorkoutNotesCard(
    progress: WorkoutProgress,
    onNotes: (String) -> Unit,
    onSubmit: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.97f),
        tonalElevation = 2.dp
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                stringResource(Res.string.workout_feedback_title),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )
            OutlinedTextField(
                modifier = Modifier.fillMaxWidth(),
                value = progress.notes,
                onValueChange = onNotes,
                label = { Text(stringResource(Res.string.workout_notes_label)) },
                placeholder = { Text(stringResource(Res.string.workout_notes_hint)) },
                minLines = 2
            )
            if (progress.submitted) {
                Text(
                    stringResource(Res.string.workout_feedback_saved),
                    style = MaterialTheme.typography.bodySmall,
                    color = AiPalette.DeepAccent
                )
            }
            Button(
                modifier = Modifier.fillMaxWidth(),
                onClick = onSubmit,
                colors = ButtonDefaults.buttonColors(
                    containerColor = AiPalette.DeepAccent,
                    contentColor = AiPalette.OnDeepAccent
                )
            ) {
                Icon(Icons.Filled.CheckCircle, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(Res.string.workout_mark_complete))
            }
        }
    }
}
