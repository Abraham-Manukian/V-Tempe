@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.vtempe.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DirectionsBike
import androidx.compose.material.icons.filled.DirectionsRun
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.SelfImprovement
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.material3.TextButton
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
import com.vtempe.ui.GlassTopBarContainer
import com.vtempe.core.designsystem.theme.AiPalette
import com.vtempe.shared.domain.exercise.ExerciseCalibrationKind
import com.vtempe.shared.domain.exercise.ExerciseDefinition
import com.vtempe.shared.domain.exercise.ExerciseLibrary
import com.vtempe.shared.domain.exercise.ExerciseTechnique
import com.vtempe.shared.domain.exercise.ExerciseVisualFamily
import com.vtempe.shared.domain.exercise.LocalizedText
import com.vtempe.shared.domain.model.CoachTrainerIds
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

// ─────────────────────────────────────────────────────────────
// Root
// ─────────────────────────────────────────────────────────────

@Composable
fun WorkoutScreen(
    onAskCoach: (String) -> Unit = {},
    onEnterActiveWorkout: () -> Unit = {},
    onExitActiveWorkout: () -> Unit = {},
    presenter: WorkoutPresenter = rememberWorkoutPresenter()
) {
    val state by presenter.state.collectAsState()
    val topBarHeight = LocalTopBarHeight.current
    val bottomBarHeight = LocalBottomBarHeight.current

    var detailWorkoutId by remember { mutableStateOf<String?>(null) }
    val detailWorkout = state.workouts.firstOrNull { it.id == detailWorkoutId }

    LaunchedEffect(detailWorkoutId) {
        if (detailWorkoutId != null) onEnterActiveWorkout() else onExitActiveWorkout()
    }

    LaunchedEffect(state.workouts, detailWorkoutId) {
        if (detailWorkoutId != null && detailWorkout == null) detailWorkoutId = null
    }

    BrandScreen(Modifier.fillMaxSize()) {
        if (detailWorkout == null) {
            WorkoutListContent(
                state = state,
                topBarHeight = topBarHeight,
                bottomBarHeight = bottomBarHeight,
                onOpenWorkout = {
                    presenter.select(it)
                    detailWorkoutId = it
                }
            )
        } else {
            val progress = state.progress[detailWorkout.id] ?: WorkoutProgress(workoutId = detailWorkout.id)
            ActiveWorkoutScreen(
                workout = detailWorkout,
                progress = progress,
                coachTrainerId = state.coachTrainerId,
                topBarHeight = topBarHeight,
                bottomBarHeight = bottomBarHeight,
                onBack = { detailWorkoutId = null },
                onAddSet = { exerciseId, reps, weight, rpe ->
                    presenter.addSet(exerciseId, reps, weight, rpe)
                },
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
}

// ─────────────────────────────────────────────────────────────
// Workout list
// ─────────────────────────────────────────────────────────────

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
private fun WorkoutListHeader(onStartWorkout: () -> Unit) {
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

// ─────────────────────────────────────────────────────────────
// Active workout
// ─────────────────────────────────────────────────────────────

@Composable
private fun ActiveWorkoutScreen(
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

// ─────────────────────────────────────────────────────────────
// Active workout header
// ─────────────────────────────────────────────────────────────

@Composable
private fun ActiveWorkoutHeader(
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

// ─────────────────────────────────────────────────────────────
// Rest timer banner
// ─────────────────────────────────────────────────────────────

@Composable
private fun RestBanner(
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

// ─────────────────────────────────────────────────────────────
// Compact exercise row in the active workout list
// ─────────────────────────────────────────────────────────────

@Composable
private fun ExerciseRow(
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

// ─────────────────────────────────────────────────────────────
// Set detail bottom sheet
// ─────────────────────────────────────────────────────────────

@Composable
private fun SetDetailSheet(
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
            contentDescription = exerciseName,
            onDismiss = { showImageFullScreen = false }
        )
    }
}

// ─────────────────────────────────────────────────────────────
// Stepper control (+/- buttons)
// ─────────────────────────────────────────────────────────────

@Composable
private fun StepperControl(
    modifier: Modifier = Modifier,
    label: String,
    value: String,
    subtitle: String? = null,
    onDecrement: () -> Unit,
    onIncrement: () -> Unit
) {
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
    ) {
        Column(
            modifier = Modifier.padding(vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(0.dp)
        ) {
            Text(
                label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (subtitle != null) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                )
            }
            Spacer(Modifier.height(2.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onDecrement, modifier = Modifier.size(40.dp)) {
                    Icon(
                        Icons.Filled.Remove,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                }
                Text(
                    value,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    modifier = Modifier.weight(1f),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
                IconButton(onClick = onIncrement, modifier = Modifier.size(40.dp)) {
                    Icon(
                        Icons.Filled.Add,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────
// Collapsible section
// ─────────────────────────────────────────────────────────────

@Composable
private fun CollapsibleSection(
    title: String,
    expanded: Boolean,
    onToggle: () -> Unit,
    content: @Composable () -> Unit
) {
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onToggle)
                .padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f)
            )
            Icon(
                imageVector = if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp)
            )
        }
        AnimatedVisibility(visible = expanded) {
            Column(modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)) {
                content()
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────
// Extra sets row
// ─────────────────────────────────────────────────────────────

@Composable
private fun ExtraSetsRow(extraSets: List<ExtraWorkoutSet>) {
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

// ─────────────────────────────────────────────────────────────
// Notes and complete card
// ─────────────────────────────────────────────────────────────

@Composable
private fun WorkoutNotesCard(
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

// ─────────────────────────────────────────────────────────────
// Fullscreen image dialog
// ─────────────────────────────────────────────────────────────

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
                modifier = Modifier.fillMaxSize().padding(16.dp),
                contentScale = ContentScale.Fit
            )
            IconButton(
                onClick = onDismiss,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(12.dp)
                    .background(Color.Black.copy(alpha = 0.55f), CircleShape)
            ) {
                Icon(Icons.Filled.Close, contentDescription = null, tint = Color.White)
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────
// Add set sheet
// ─────────────────────────────────────────────────────────────

@Composable
private fun AddSetSheet(
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

// ─────────────────────────────────────────────────────────────
// StatusPill (shared between list and sheet)
// ─────────────────────────────────────────────────────────────

@Composable
private fun StatusPill(label: String, tint: Color) {
    Surface(shape = CircleShape, color = tint.copy(alpha = 0.12f)) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            style = MaterialTheme.typography.labelMedium,
            color = tint,
            fontWeight = FontWeight.SemiBold
        )
    }
}

// ─────────────────────────────────────────────────────────────
// Exercise data helpers
// ─────────────────────────────────────────────────────────────

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
        ?: exerciseId.replace('_', ' ').replaceFirstChar {
            if (it.isLowerCase()) it.titlecase() else it.toString()
        }

@Composable
private fun exerciseGuide(
    exerciseId: String,
    coachTrainerId: String = CoachTrainerIds.DEFAULT
): ExerciseGuideData {
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
        ExerciseVisualFamily.CARDIO ->
            if (exerciseId.trim().lowercase() == "bike") Icons.Filled.DirectionsBike
            else Icons.Filled.DirectionsRun
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

// ─────────────────────────────────────────────────────────────
// String/format helpers
// ─────────────────────────────────────────────────────────────

@Composable
private fun plannedSetSummary(set: WorkoutSet): String {
    val weightStr = set.weightKg?.let {
        stringResource(Res.string.workout_weight_display).kmpFormat(it)
    } ?: stringResource(Res.string.workout_weight_bodyweight)
    val rpeStr = set.rpe?.let {
        stringResource(Res.string.workout_rpe_suffix).kmpFormat(it.toEditableDecimal())
    }.orEmpty()
    return stringResource(Res.string.workout_set_summary).kmpFormat(set.reps, weightStr) + rpeStr
}

@Composable
private fun buildPerformedSummary(performed: PerformedSet): String {
    val repsLabel = stringResource(Res.string.workout_reps_unit)
    return buildList {
        performed.actualReps?.let { add("$it $repsLabel") }
        performed.actualWeightKg?.let {
            add(stringResource(Res.string.workout_weight_display).kmpFormat(it))
        }
        performed.actualRpe?.let { add("RPE ${it.toEditableDecimal()}") }
    }.joinToString(" · ").ifEmpty { "—" }
}

private fun Double.toEditableDecimal(): String =
    if (this % 1.0 == 0.0) roundToInt().toString() else toString()

private fun formatDuration(seconds: Int): String {
    val s = seconds.coerceAtLeast(0)
    return "${(s / 60).toString().padStart(2, '0')}:${(s % 60).toString().padStart(2, '0')}"
}

@Composable
private fun workoutCardColors() =
    CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.97f))

@Composable
private fun workoutCardElevation() =
    CardDefaults.cardElevation(defaultElevation = 8.dp)
