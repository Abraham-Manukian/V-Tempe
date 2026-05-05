@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.vtempe.ui.screens

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DirectionsBike
import androidx.compose.material.icons.filled.DirectionsRun
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SelfImprovement
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.intl.Locale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.vtempe.core.designsystem.components.BrandScreen
import com.vtempe.core.designsystem.theme.AiPalette
import com.vtempe.shared.domain.exercise.ExerciseCalibrationKind
import com.vtempe.shared.domain.exercise.ExerciseDefinition
import com.vtempe.shared.domain.exercise.ExerciseLibrary
import com.vtempe.shared.domain.exercise.ExerciseTechnique
import com.vtempe.shared.domain.exercise.ExerciseVisualFamily
import com.vtempe.shared.domain.exercise.LocalizedText
import com.vtempe.shared.domain.model.ExtraWorkoutSet
import com.vtempe.shared.domain.model.PerformedSet
import com.vtempe.shared.domain.model.Workout
import com.vtempe.shared.domain.model.WorkoutProgress
import com.vtempe.shared.domain.model.WorkoutSet
import com.vtempe.ui.LocalBottomBarHeight
import com.vtempe.ui.LocalTopBarHeight
import com.vtempe.ui.Res
import com.vtempe.ui.*
import com.vtempe.ui.util.kmpFormat
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import kotlin.math.roundToInt

@Composable
fun WorkoutScreen(
    onAskCoach: (String) -> Unit = {},
    presenter: WorkoutPresenter = rememberWorkoutPresenter()
) {
    val state by presenter.state.collectAsState()
    val topBarHeight = LocalTopBarHeight.current
    val bottomBarHeight = LocalBottomBarHeight.current

    var showAddSheet by remember { mutableStateOf(false) }
    var detailWorkoutId by remember { mutableStateOf<String?>(null) }

    val detailWorkout = state.workouts.firstOrNull { it.id == detailWorkoutId }

    LaunchedEffect(state.workouts, detailWorkoutId) {
        if (detailWorkoutId != null && detailWorkout == null) {
            detailWorkoutId = null
        }
    }
    LaunchedEffect(detailWorkoutId) {
        if (detailWorkoutId == null) showAddSheet = false
    }

    BrandScreen(Modifier.fillMaxSize()) {
        if (detailWorkout == null) {
            WorkoutListContent(
                state = state,
                topBarHeight = topBarHeight,
                bottomBarHeight = bottomBarHeight,
                onOpenWorkout = { workoutId ->
                    presenter.select(workoutId)
                    detailWorkoutId = workoutId
                }
            )
        } else {
            val progress = state.progress[detailWorkout.id] ?: WorkoutProgress(workoutId = detailWorkout.id)
            WorkoutDetailContent(
                workout = detailWorkout,
                progress = progress,
                coachTrainerId = state.coachTrainerId,
                topBarHeight = topBarHeight,
                bottomBarHeight = bottomBarHeight,
                onBack = { detailWorkoutId = null },
                onAddSet = { showAddSheet = true },
                onResultChanged = { setIndex, completed, reps, weight, rpe ->
                    presenter.updatePerformedSet(detailWorkout.id, setIndex, completed, reps, weight, rpe)
                },
                onNotesChanged = { presenter.updateNotes(detailWorkout.id, it) },
                onRestSecondsChanged = { presenter.updateRestSeconds(detailWorkout.id, it) },
                onSubmit = { presenter.submitFeedback(detailWorkout.id) },
                onAskCoach = onAskCoach
            )
        }
    }

    if (showAddSheet && detailWorkout != null) {
        AddSetSheet(
            onDismiss = { showAddSheet = false },
            onAdd = { exercise, reps, weight, rpe ->
                presenter.addSet(exercise, reps, weight, rpe)
                showAddSheet = false
            }
        )
    }
}

@Composable
private fun WorkoutListContent(
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
                    WorkoutListIntro(
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
private fun WorkoutListIntro(onStartWorkout: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = workoutCardColors(),
        elevation = workoutCardElevation()
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                stringResource(Res.string.workout_plan_title),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
            Text(
                stringResource(Res.string.workout_plan_intro),
                color = MaterialTheme.colorScheme.onSurfaceVariant
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
private fun WorkoutEmptyState() {
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
private fun WorkoutOverviewCard(
    workout: Workout,
    progress: WorkoutProgress,
    selected: Boolean,
    onOpen: () -> Unit
) {
    val completedCount = progress.performedSets.count { it.completed }
    val progressValue = if (workout.sets.isEmpty()) 0f else completedCount.toFloat() / workout.sets.size.toFloat()
    val previewSets = workout.sets.take(3)
    val remaining = (workout.sets.size - previewSets.size).coerceAtLeast(0)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpen),
        colors = workoutCardColors(),
        elevation = workoutCardElevation(),
        border = if (selected) BorderStroke(1.5.dp, AiPalette.DeepAccent.copy(alpha = 0.45f)) else null,
        shape = MaterialTheme.shapes.large
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            WorkoutCardHeader(
                title = exerciseLabel(workout.sets.firstOrNull()?.exerciseId ?: "workout"),
                date = workout.date.toString(),
                setsCount = workout.sets.size,
                completed = completedCount,
                progress = progressValue
            )
            LinearProgressIndicator(
                progress = { progressValue },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(CircleShape),
                trackColor = MaterialTheme.colorScheme.surfaceVariant,
                color = AiPalette.DeepAccent
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
            previewSets.forEachIndexed { index, set ->
                WorkoutSetPreviewRow(
                    set = set,
                    completed = progress.performedSets.any { it.setIndex == index && it.completed }
                )
            }
            if (remaining > 0) {
                Text(
                    stringResource(Res.string.workout_more_exercises).kmpFormat(remaining),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (progress.notes.isNotBlank()) {
                    StatusPill(
                        label = stringResource(Res.string.workout_coach_notes_added),
                        tint = MaterialTheme.colorScheme.secondary
                    )
                }
                if (progress.extraSets.isNotEmpty()) {
                    StatusPill(
                        label = stringResource(Res.string.workout_extra_sets_count).kmpFormat(progress.extraSets.size),
                        tint = MaterialTheme.colorScheme.tertiary
                    )
                }
                if (progress.submitted) {
                    StatusPill(
                        label = stringResource(Res.string.workout_saved),
                        tint = AiPalette.DeepAccent
                    )
                }
            }
            FilledTonalButton(
                modifier = Modifier.fillMaxWidth(),
                onClick = onOpen
            ) {
                Text(stringResource(Res.string.workout_open_details))
            }
        }
    }
}

@Composable
private fun WorkoutSetPreviewRow(set: WorkoutSet, completed: Boolean) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = if (completed) Icons.Filled.CheckCircle else exerciseGuide(set.exerciseId).icon,
            contentDescription = null,
            tint = if (completed) AiPalette.DeepAccent else MaterialTheme.colorScheme.primary
        )
        Spacer(Modifier.width(12.dp))
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                exerciseLabel(set.exerciseId),
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                plannedSetSummary(set),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
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
            color = AiPalette.DeepAccent
        )
    }
}

@Composable
private fun WorkoutDetailContent(
    workout: Workout,
    progress: WorkoutProgress,
    coachTrainerId: String,
    topBarHeight: Dp,
    bottomBarHeight: Dp,
    onBack: () -> Unit,
    onAddSet: () -> Unit,
    onResultChanged: (Int, Boolean, Int?, Double?, Double?) -> Unit,
    onNotesChanged: (String) -> Unit,
    onRestSecondsChanged: (Int) -> Unit,
    onSubmit: () -> Unit,
    onAskCoach: (String) -> Unit
) {
    var sessionSeconds by remember(workout.id) { mutableIntStateOf(0) }
    var restRemaining by remember(workout.id) { mutableIntStateOf(progress.restSeconds) }
    var isRestRunning by remember(workout.id) { mutableStateOf(false) }

    LaunchedEffect(workout.id) {
        sessionSeconds = 0
        while (true) {
            delay(1_000)
            sessionSeconds += 1
        }
    }
    LaunchedEffect(workout.id, progress.restSeconds, isRestRunning) {
        if (!isRestRunning) restRemaining = progress.restSeconds
    }
    LaunchedEffect(workout.id, isRestRunning) {
        if (!isRestRunning) return@LaunchedEffect
        while (isRestRunning && restRemaining > 0) {
            delay(1_000)
            restRemaining -= 1
        }
        if (restRemaining <= 0) isRestRunning = false
    }

    Scaffold(
        containerColor = Color.Transparent,
        floatingActionButton = {
            FloatingActionButton(
                modifier = Modifier.padding(bottom = bottomBarHeight),
                onClick = onAddSet
            ) {
                Icon(Icons.Filled.Add, contentDescription = stringResource(Res.string.workout_add_button))
            }
        }
    ) { _ ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                top = topBarHeight + 16.dp,
                bottom = bottomBarHeight + 92.dp,
                start = 20.dp,
                end = 20.dp
            ),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                WorkoutDetailHeader(
                    title = stringResource(Res.string.workout_details_title),
                    subtitle = workout.date.toString(),
                    onBack = onBack
                )
            }
            item { WorkoutSummaryCard(workout, progress, sessionSeconds) }
            item {
                RestTimerCard(
                    restSeconds = progress.restSeconds,
                    remainingSeconds = restRemaining,
                    isRunning = isRestRunning,
                    onPreset = {
                        onRestSecondsChanged(it)
                        if (!isRestRunning) restRemaining = it
                    },
                    onToggle = {
                        if (isRestRunning) {
                            isRestRunning = false
                        } else {
                            restRemaining = progress.restSeconds
                            isRestRunning = true
                        }
                    }
                )
            }
            itemsIndexed(workout.sets, key = { index, set -> "${workout.id}-$index-${set.exerciseId}" }) { index, set ->
                ExerciseDetailCard(
                    workoutId = workout.id,
                    setIndex = index,
                    plannedSet = set,
                    performed = progress.performedSets.firstOrNull { it.setIndex == index },
                    coachTrainerId = coachTrainerId,
                    onResultChanged = { completed, reps, weight, rpe ->
                        onResultChanged(index, completed, reps, weight, rpe)
                    },
                    onToggleComplete = { completed, reps, weight, rpe ->
                        onResultChanged(index, completed, reps, weight, rpe)
                        if (completed) {
                            restRemaining = progress.restSeconds
                            isRestRunning = true
                        } else {
                            isRestRunning = false
                        }
                    },
                    onUseSuggestedRest = {
                        onRestSecondsChanged(it)
                        if (!isRestRunning) restRemaining = it
                    },
                    onAskCoach = onAskCoach
                )
            }
            if (progress.extraSets.isNotEmpty()) {
                item { ExtraSetsCard(progress.extraSets) }
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

@Composable
private fun WorkoutDetailHeader(
    title: String,
    subtitle: String,
    onBack: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onBack) {
            Icon(Icons.Filled.ArrowBack, contentDescription = stringResource(Res.string.action_back))
        }
        Spacer(Modifier.width(8.dp))
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun WorkoutSummaryCard(
    workout: Workout,
    progress: WorkoutProgress,
    sessionSeconds: Int
) {
    val completed = progress.performedSets.count { it.completed }
    val progressValue = if (workout.sets.isEmpty()) 0f else completed.toFloat() / workout.sets.size.toFloat()

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = workoutCardColors(),
        elevation = workoutCardElevation()
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                exerciseLabel(workout.sets.firstOrNull()?.exerciseId ?: "workout"),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
            Text(workout.date.toString(), color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatBadge(
                    title = stringResource(Res.string.workout_stat_done),
                    value = "$completed/${workout.sets.size}",
                    modifier = Modifier.weight(1f)
                )
                StatBadge(
                    title = stringResource(Res.string.workout_stat_progress),
                    value = "${(progressValue * 100).roundToInt()}%",
                    modifier = Modifier.weight(1f)
                )
                StatBadge(
                    title = stringResource(Res.string.workout_stat_session),
                    value = formatDuration(sessionSeconds),
                    modifier = Modifier.weight(1f)
                )
            }
            Text(
                stringResource(Res.string.workout_summary_help),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun StatBadge(title: String, value: String, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                title,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun RestTimerCard(
    restSeconds: Int,
    remainingSeconds: Int,
    isRunning: Boolean,
    onPreset: (Int) -> Unit,
    onToggle: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = workoutCardColors(),
        elevation = workoutCardElevation()
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Timer, contentDescription = null, tint = AiPalette.DeepAccent)
                    Text(
                        stringResource(Res.string.workout_rest_timer_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
                Text(
                    formatDuration(remainingSeconds),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(60, 90, 120).forEach { preset ->
                    OutlinedButton(
                        onClick = { onPreset(preset) },
                        colors = ButtonDefaults.outlinedButtonColors(
                            containerColor = if (preset == restSeconds) {
                                AiPalette.DeepAccent.copy(alpha = 0.12f)
                            } else {
                                MaterialTheme.colorScheme.surface
                            }
                        )
                    ) {
                        Text(stringResource(Res.string.workout_rest_seconds_compact).kmpFormat(preset))
                    }
                }
            }
            FilledTonalButton(onClick = onToggle) {
                Icon(
                    if (isRunning) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    contentDescription = null
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    if (isRunning) stringResource(Res.string.workout_rest_stop)
                    else stringResource(Res.string.workout_rest_start)
                )
            }
        }
    }
}

@Composable
private fun ExerciseDetailCard(
    workoutId: String,
    setIndex: Int,
    plannedSet: WorkoutSet,
    performed: PerformedSet?,
    coachTrainerId: String,
    onResultChanged: (Boolean, Int?, Double?, Double?) -> Unit,
    onToggleComplete: (Boolean, Int?, Double?, Double?) -> Unit,
    onUseSuggestedRest: (Int) -> Unit,
    onAskCoach: (String) -> Unit
) {
    val guide = exerciseGuide(plannedSet.exerciseId, coachTrainerId)
    val exerciseName = exerciseLabel(plannedSet.exerciseId)
    val askCoachPrompt = stringResource(Res.string.workout_ask_coach_prompt).kmpFormat(exerciseName)
    var repsInput by remember(workoutId, setIndex, performed?.actualReps, plannedSet.reps) {
        mutableStateOf((performed?.actualReps ?: plannedSet.reps).toString())
    }
    var weightInput by remember(workoutId, setIndex, performed?.actualWeightKg, plannedSet.weightKg) {
        mutableStateOf(performed?.actualWeightKg?.toEditableDecimal() ?: plannedSet.weightKg?.toEditableDecimal().orEmpty())
    }
    var rpeInput by remember(workoutId, setIndex, performed?.actualRpe, plannedSet.rpe) {
        mutableStateOf(performed?.actualRpe?.toEditableDecimal() ?: plannedSet.rpe?.toEditableDecimal().orEmpty())
    }
    var showImageFullScreen by remember(workoutId, setIndex, guide.illustration) { mutableStateOf(false) }
    val completed = performed?.completed == true

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = workoutCardColors(),
        elevation = workoutCardElevation()
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { showImageFullScreen = true },
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
            ) {
                Box {
                    Image(
                        painter = painterResource(guide.illustration),
                        contentDescription = exerciseName,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(320.dp)
                            .padding(8.dp),
                        contentScale = ContentScale.Fit
                    )
                    Surface(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(12.dp),
                        shape = CircleShape,
                        color = Color.Black.copy(alpha = 0.56f)
                    ) {
                        Text(
                            text = stringResource(Res.string.workout_image_fullscreen_hint),
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            style = MaterialTheme.typography.labelMedium,
                            color = Color.White,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }

            Column(
                modifier = Modifier.padding(start = 18.dp, end = 18.dp, bottom = 18.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top
                ) {
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            exerciseName,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Text(guide.description, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    StatusPill(
                        label = if (completed) {
                            stringResource(Res.string.workout_status_done)
                        } else {
                            stringResource(Res.string.workout_status_planned)
                        },
                        tint = if (completed) AiPalette.DeepAccent else MaterialTheme.colorScheme.primary
                    )
                }

                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(guide.focus, key = { it }) { focus -> FocusChip(focus) }
                }

                Surface(
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            stringResource(Res.string.workout_target_set),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            plannedSetSummary(plannedSet),
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                GuideSection(title = stringResource(Res.string.workout_how_to_perform)) {
                    guide.steps.forEachIndexed { index, step ->
                        Text(stringResource(Res.string.workout_step_line).kmpFormat(index + 1, step))
                    }
                }

                GuideSection(title = stringResource(Res.string.workout_coach_cue)) {
                    Text(guide.cue, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }

                Surface(
                    shape = MaterialTheme.shapes.medium,
                    color = AiPalette.DeepAccent.copy(alpha = 0.08f)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(
                                stringResource(Res.string.workout_suggested_rest),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                stringResource(Res.string.workout_rest_seconds_display)
                                    .kmpFormat(guide.defaultRestSeconds),
                                fontWeight = FontWeight.Bold
                            )
                        }
                        OutlinedButton(onClick = { onUseSuggestedRest(guide.defaultRestSeconds) }) {
                            Text(stringResource(Res.string.workout_use_preset))
                        }
                    }
                }

                Surface(
                    shape = MaterialTheme.shapes.large,
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.28f)
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            stringResource(Res.string.workout_training_controls),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            MetricField(
                                modifier = Modifier.weight(1f),
                                label = stringResource(Res.string.workout_reps_label),
                                value = repsInput
                            ) {
                                repsInput = it.filter(Char::isDigit).take(3)
                                onResultChanged(
                                    completed,
                                    repsInput.toIntOrNull(),
                                    weightInput.toDoubleOrNullSafe(),
                                    rpeInput.toDoubleOrNullSafe()
                                )
                            }
                            MetricField(
                                modifier = Modifier.weight(1f),
                                label = stringResource(Res.string.workout_weight_label),
                                value = weightInput
                            ) {
                                weightInput = sanitizeDecimalInput(it)
                                onResultChanged(
                                    completed,
                                    repsInput.toIntOrNull(),
                                    weightInput.toDoubleOrNullSafe(),
                                    rpeInput.toDoubleOrNullSafe()
                                )
                            }
                            MetricField(
                                modifier = Modifier.weight(1f),
                                label = stringResource(Res.string.workout_rpe_label),
                                value = rpeInput
                            ) {
                                rpeInput = sanitizeDecimalInput(it)
                                onResultChanged(
                                    completed,
                                    repsInput.toIntOrNull(),
                                    weightInput.toDoubleOrNullSafe(),
                                    rpeInput.toDoubleOrNullSafe()
                                )
                            }
                        }
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(
                        modifier = Modifier.weight(1f),
                        onClick = {
                            onResultChanged(
                                completed,
                                repsInput.toIntOrNull(),
                                weightInput.toDoubleOrNullSafe(),
                                rpeInput.toDoubleOrNullSafe()
                            )
                        }
                    ) {
                        Text(stringResource(Res.string.workout_save_result))
                    }
                    Button(
                        modifier = Modifier.weight(1f),
                        onClick = {
                            onToggleComplete(
                                !completed,
                                repsInput.toIntOrNull(),
                                weightInput.toDoubleOrNullSafe(),
                                rpeInput.toDoubleOrNullSafe()
                            )
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = AiPalette.DeepAccent,
                            contentColor = AiPalette.OnDeepAccent
                        )
                    ) {
                        Text(
                            if (completed) {
                                stringResource(Res.string.workout_mark_not_done)
                            } else {
                                stringResource(Res.string.workout_mark_set_done)
                            }
                        )
                    }
                }
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
    }

    if (showImageFullScreen) {
        ExerciseImageDialog(
            illustration = guide.illustration,
            contentDescription = exerciseName,
            onDismiss = { showImageFullScreen = false }
        )
    }
}

@Composable
private fun ExerciseImageDialog(
    illustration: DrawableResource,
    contentDescription: String,
    onDismiss: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
        ) {
            Image(
                painter = painterResource(illustration),
                contentDescription = contentDescription,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                contentScale = ContentScale.Fit
            )
            IconButton(
                onClick = onDismiss,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(12.dp)
                    .background(Color.Black.copy(alpha = 0.55f), CircleShape)
            ) {
                Icon(
                    imageVector = Icons.Filled.Close,
                    contentDescription = null,
                    tint = Color.White
                )
            }
        }
    }
}

@Composable
private fun GuideSection(
    title: String,
    content: @Composable () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        content()
    }
}

@Composable
private fun FocusChip(text: String) {
    Surface(
        shape = CircleShape,
        color = AiPalette.DeepAccent.copy(alpha = 0.12f)
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            style = MaterialTheme.typography.labelMedium,
            color = AiPalette.DeepAccent
        )
    }
}

@Composable
private fun StatusPill(label: String, tint: Color) {
    Surface(
        shape = CircleShape,
        color = tint.copy(alpha = 0.12f)
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            style = MaterialTheme.typography.labelMedium,
            color = tint,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun MetricField(
    modifier: Modifier,
    label: String,
    value: String,
    onValueChange: (String) -> Unit
) {
    OutlinedTextField(
        modifier = modifier,
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        singleLine = true
    )
}

@Composable
private fun ExtraSetsCard(extraSets: List<ExtraWorkoutSet>) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = workoutCardColors(),
        elevation = workoutCardElevation()
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                stringResource(Res.string.workout_extra_sets_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            extraSets.forEach { extraSet ->
                Surface(
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(exerciseLabel(extraSet.exerciseId), fontWeight = FontWeight.SemiBold)
                        Text(
                            plannedSetSummary(extraSet),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun WorkoutNotesCard(
    progress: WorkoutProgress,
    onNotes: (String) -> Unit,
    onSubmit: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = workoutCardColors(),
        elevation = workoutCardElevation()
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                stringResource(Res.string.workout_feedback_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                stringResource(Res.string.workout_feedback_help),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            OutlinedTextField(
                modifier = Modifier.fillMaxWidth(),
                value = progress.notes,
                onValueChange = onNotes,
                label = { Text(stringResource(Res.string.workout_notes_label)) },
                placeholder = { Text(stringResource(Res.string.workout_notes_hint)) }
            )
            if (progress.submitted) {
                Text(
                    stringResource(Res.string.workout_feedback_saved),
                    color = AiPalette.DeepAccent
                )
            }
            Button(
                onClick = onSubmit,
                colors = ButtonDefaults.buttonColors(
                    containerColor = AiPalette.DeepAccent,
                    contentColor = AiPalette.OnDeepAccent
                )
            ) {
                Text(stringResource(Res.string.workout_mark_complete))
            }
        }
    }
}

@Composable
private fun AddSetSheet(
    onDismiss: () -> Unit,
    onAdd: (String, Int, Double?, Double?) -> Unit
) {
    var exercise by remember { mutableStateOf("") }
    var reps by remember { mutableStateOf("10") }
    var weight by remember { mutableStateOf("") }
    var rpe by remember { mutableStateOf("") }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
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
                label = { Text(stringResource(Res.string.workout_exercise_label)) }
            )
            OutlinedTextField(
                modifier = Modifier.fillMaxWidth(),
                value = reps,
                onValueChange = { reps = it.filter(Char::isDigit).take(3) },
                label = { Text(stringResource(Res.string.workout_reps_label)) },
                singleLine = true
            )
            OutlinedTextField(
                modifier = Modifier.fillMaxWidth(),
                value = weight,
                onValueChange = { weight = sanitizeDecimalInput(it) },
                label = { Text(stringResource(Res.string.workout_weight_label)) },
                singleLine = true
            )
            OutlinedTextField(
                modifier = Modifier.fillMaxWidth(),
                value = rpe,
                onValueChange = { rpe = sanitizeDecimalInput(it) },
                label = { Text(stringResource(Res.string.workout_rpe_label)) },
                singleLine = true
            )
            FilledTonalButton(
                onClick = {
                    onAdd(
                        exercise.ifBlank { "custom" },
                        reps.toIntOrNull() ?: 0,
                        weight.toDoubleOrNullSafe(),
                        rpe.toDoubleOrNullSafe()
                    )
                }
            ) {
                Text(stringResource(Res.string.workout_add_button))
            }
        }
    }
}

private data class ExerciseGuideData(
    val illustration: DrawableResource,
    val icon: ImageVector,
    val description: String,
    val focus: List<String>,
    val cue: String,
    val steps: List<String>,
    val defaultRestSeconds: Int
)

@Composable
private fun exerciseLabel(exerciseId: String): String =
    ExerciseLibrary.findByIdOrAlias(exerciseId)
        ?.name
        ?.resolve(Locale.current.language)
        ?: exerciseId.replace('_', ' ').replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }

@Composable
private fun exerciseGuide(exerciseId: String, coachTrainerId: String = normalizeCoachTrainerId(null)): ExerciseGuideData {
    val localeTag = Locale.current.language
    val definition = ExerciseLibrary.findByIdOrAlias(exerciseId) ?: genericExerciseDefinition()
    val technique = definition.technique
    val fallbackIllustration = illustrationFor(definition.visualFamily)
    return ExerciseGuideData(
        illustration = coachExerciseIllustration(coachTrainerId, definition.id, fallbackIllustration),
        icon = iconFor(definition.visualFamily, definition.id),
        description = technique.summary.resolve(localeTag),
        focus = technique.focus(localeTag),
        cue = technique.keyCue.resolve(localeTag),
        steps = technique.steps(localeTag),
        defaultRestSeconds = technique.defaultRestSeconds
    )
}

private fun illustrationFor(family: ExerciseVisualFamily): DrawableResource =
    when (family) {
        ExerciseVisualFamily.LOWER_BODY -> Res.drawable.workout_illustration_lower_body
        ExerciseVisualFamily.PUSH -> Res.drawable.workout_illustration_push
        ExerciseVisualFamily.PULL -> Res.drawable.workout_illustration_pull
        ExerciseVisualFamily.OVERHEAD -> Res.drawable.workout_illustration_overhead
        ExerciseVisualFamily.ARMS -> Res.drawable.workout_illustration_arms
        ExerciseVisualFamily.CORE -> Res.drawable.workout_illustration_core
        ExerciseVisualFamily.CARDIO -> Res.drawable.workout_illustration_cardio
        ExerciseVisualFamily.GENERIC -> Res.drawable.workout_illustration_generic
    }

private fun iconFor(family: ExerciseVisualFamily, exerciseId: String): ImageVector =
    when (family) {
        ExerciseVisualFamily.LOWER_BODY -> Icons.Filled.DirectionsRun
        ExerciseVisualFamily.CORE -> Icons.Filled.SelfImprovement
        ExerciseVisualFamily.CARDIO -> if (normalizeExerciseId(exerciseId) == "bike") Icons.Filled.DirectionsBike else Icons.Filled.DirectionsRun
        else -> Icons.Filled.FitnessCenter
    }

private fun genericExerciseDefinition(): ExerciseDefinition = ExerciseDefinition(
    id = "generic",
    name = LocalizedText("Exercise", "Упражнение"),
    muscleGroups = emptyList(),
    difficulty = 1,
    visualFamily = ExerciseVisualFamily.GENERIC,
    calibrationKind = ExerciseCalibrationKind.WEIGHT_AND_REPS,
    calibrationHint = LocalizedText(
        "Pick a load you can control with clean technique.",
        "Подбери нагрузку, которую можешь контролировать с чистой техникой."
    ),
    imagePrompt = "",
    technique = ExerciseTechnique(
        summary = LocalizedText(
            "Controlled strength work with focus on repeat quality and technique stability.",
            "Контролируемая силовая работа с акцентом на качество повторений и устойчивую технику."
        ),
        focusEn = listOf("Strength"),
        focusRu = listOf("Сила"),
        keyCue = LocalizedText(
            "Move smoothly and stop the set before technique breaks down.",
            "Двигайся плавно и заканчивай подход до того, как техника начнет ломаться."
        ),
        stepsEn = listOf(
            "Set a stable start position.",
            "Control the main phase of every rep.",
            "Finish balanced and ready for the next repetition."
        ),
        stepsRu = listOf(
            "Займи устойчивый старт.",
            "Контролируй основную фазу каждого повтора.",
            "Заканчивай в балансе и готовности к следующему повтору."
        ),
        defaultRestSeconds = 75
    )
)

@Composable
private fun plannedSetSummary(set: WorkoutSet): String {
    val weight = set.weightKg?.let {
        stringResource(Res.string.workout_weight_display).kmpFormat(it)
    } ?: stringResource(Res.string.workout_weight_bodyweight)
    val rpe = set.rpe?.let {
        stringResource(Res.string.workout_rpe_suffix).kmpFormat(it.toEditableDecimal())
    }.orEmpty()
    return stringResource(Res.string.workout_set_summary).kmpFormat(set.reps, weight) + rpe
}

@Composable
private fun plannedSetSummary(set: ExtraWorkoutSet): String =
    plannedSetSummary(WorkoutSet(set.exerciseId, set.reps, set.weightKg, set.rpe))

private fun normalizeExerciseId(exerciseId: String): String =
    exerciseId.trim().lowercase().replace('-', '_').replace(' ', '_')

private fun Double.toEditableDecimal(): String =
    if (this % 1.0 == 0.0) roundToInt().toString() else toString()

private fun sanitizeDecimalInput(value: String): String =
    value.filter { it.isDigit() || it == '.' || it == ',' }.replace(',', '.').take(5)

private fun String.toDoubleOrNullSafe(): Double? = replace(',', '.').toDoubleOrNull()

private fun formatDuration(seconds: Int): String =
    "${(seconds.coerceAtLeast(0) / 60).toString().padStart(2, '0')}:${(seconds.coerceAtLeast(0) % 60).toString().padStart(2, '0')}"

@Composable
private fun workoutCardColors() =
    CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.97f))

@Composable
private fun workoutCardElevation() =
    CardDefaults.cardElevation(defaultElevation = 8.dp)

