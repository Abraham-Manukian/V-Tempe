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
import com.vtempe.core.designsystem.components.BrandScreen
import com.vtempe.core.designsystem.theme.AiPalette
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
fun WorkoutScreen(presenter: WorkoutPresenter = rememberWorkoutPresenter()) {
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
                topBarHeight = topBarHeight,
                bottomBarHeight = bottomBarHeight,
                onBack = { detailWorkoutId = null },
                onAddSet = { showAddSheet = true },
                onResultChanged = { setIndex, completed, reps, weight, rpe ->
                    presenter.updatePerformedSet(detailWorkout.id, setIndex, completed, reps, weight, rpe)
                },
                onNotesChanged = { presenter.updateNotes(detailWorkout.id, it) },
                onRestSecondsChanged = { presenter.updateRestSeconds(detailWorkout.id, it) },
                onSubmit = { presenter.submitFeedback(detailWorkout.id) }
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
                item { WorkoutListIntro() }
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
private fun WorkoutListIntro() {
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
                workoutCopy("Workout plan", "План тренировок"),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
            Text(
                workoutCopy(
                    "Keep the familiar card view. Tap any card to open a detailed session with exercise visuals, cues, timers, and result logging.",
                    "Оставляем привычные карточки. Нажми на любую карточку, чтобы открыть подробную сессию с картинками упражнений, подсказками, таймерами и фиксацией результата."
                ),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
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
                workoutCopy("No workout loaded yet", "Тренировка ещё не загружена"),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            Text(
                workoutCopy(
                    "Finish onboarding or wait for coach sync to generate your first plan.",
                    "Заверши онбординг или дождись синхронизации коуча, чтобы получить первый план."
                ),
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
                    workoutCopy("+$remaining more exercises inside", "+$remaining упражнений внутри"),
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
                        label = workoutCopy("Coach notes added", "Есть заметки"),
                        tint = MaterialTheme.colorScheme.secondary
                    )
                }
                if (progress.extraSets.isNotEmpty()) {
                    StatusPill(
                        label = workoutCopy("${progress.extraSets.size} extra sets", "${progress.extraSets.size} доп. подходов"),
                        tint = MaterialTheme.colorScheme.tertiary
                    )
                }
                if (progress.submitted) {
                    StatusPill(
                        label = workoutCopy("Saved", "Сохранено"),
                        tint = AiPalette.DeepAccent
                    )
                }
            }
            FilledTonalButton(
                modifier = Modifier.fillMaxWidth(),
                onClick = onOpen
            ) {
                Text(workoutCopy("Open workout details", "Открыть детали тренировки"))
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
    topBarHeight: Dp,
    bottomBarHeight: Dp,
    onBack: () -> Unit,
    onAddSet: () -> Unit,
    onResultChanged: (Int, Boolean, Int?, Double?, Double?) -> Unit,
    onNotesChanged: (String) -> Unit,
    onRestSecondsChanged: (Int) -> Unit,
    onSubmit: () -> Unit
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
                    title = workoutCopy("Workout details", "Детали тренировки"),
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
                    }
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
                    title = workoutCopy("Done", "Сделано"),
                    value = "$completed/${workout.sets.size}",
                    modifier = Modifier.weight(1f)
                )
                StatBadge(
                    title = workoutCopy("Progress", "Прогресс"),
                    value = "${(progressValue * 100).roundToInt()}%",
                    modifier = Modifier.weight(1f)
                )
                StatBadge(
                    title = workoutCopy("Session", "Сессия"),
                    value = formatDuration(sessionSeconds),
                    modifier = Modifier.weight(1f)
                )
            }
            Text(
                workoutCopy(
                    "Each card below shows the movement visually, explains the technique, and lets you save actual reps, weight, and effort for AI adaptation.",
                    "Ниже каждая карточка показывает упражнение визуально, объясняет технику и даёт сохранить фактические повторы, вес и усилие для адаптации AI."
                ),
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
                        Text("${preset}s")
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
    onResultChanged: (Boolean, Int?, Double?, Double?) -> Unit,
    onToggleComplete: (Boolean, Int?, Double?, Double?) -> Unit,
    onUseSuggestedRest: (Int) -> Unit
) {
    val guide = exerciseGuide(plannedSet.exerciseId)
    var repsInput by remember(workoutId, setIndex, performed?.actualReps, plannedSet.reps) {
        mutableStateOf((performed?.actualReps ?: plannedSet.reps).toString())
    }
    var weightInput by remember(workoutId, setIndex, performed?.actualWeightKg, plannedSet.weightKg) {
        mutableStateOf(performed?.actualWeightKg?.toEditableDecimal() ?: plannedSet.weightKg?.toEditableDecimal().orEmpty())
    }
    var rpeInput by remember(workoutId, setIndex, performed?.actualRpe, plannedSet.rpe) {
        mutableStateOf(performed?.actualRpe?.toEditableDecimal() ?: plannedSet.rpe?.toEditableDecimal().orEmpty())
    }
    val completed = performed?.completed == true

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = workoutCardColors(),
        elevation = workoutCardElevation()
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
            ) {
                Image(
                    painter = painterResource(guide.illustration),
                    contentDescription = exerciseLabel(plannedSet.exerciseId),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp)
                        .padding(14.dp),
                    contentScale = ContentScale.Fit
                )
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
                            exerciseLabel(plannedSet.exerciseId),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Text(guide.description, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    StatusPill(
                        label = if (completed) workoutCopy("Done", "Готово") else workoutCopy("Planned", "План"),
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
                            workoutCopy("Target for this set", "Цель этого подхода"),
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

                GuideSection(title = workoutCopy("How to perform", "Как выполнять")) {
                    guide.steps.forEachIndexed { index, step -> Text("${index + 1}. $step") }
                }

                GuideSection(title = workoutCopy("Coach cue", "Подсказка тренера")) {
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
                                workoutCopy("Suggested rest", "Рекомендуемый отдых"),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text("${guide.defaultRestSeconds} s", fontWeight = FontWeight.Bold)
                        }
                        OutlinedButton(onClick = { onUseSuggestedRest(guide.defaultRestSeconds) }) {
                            Text(workoutCopy("Use preset", "Применить"))
                        }
                    }
                }

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
                        Text(workoutCopy("Save result", "Сохранить результат"))
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
                            if (completed) workoutCopy("Mark as not done", "Снять выполнение")
                            else workoutCopy("Mark set done", "Отметить подход")
                        )
                    }
                }
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
                workoutCopy("Extra sets", "Дополнительные подходы"),
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
                workoutCopy(
                    "Save how the session felt so the next AI plan can react to real fatigue, volume, and notes.",
                    "Сохрани, как ощущалась сессия, чтобы следующий AI-план учитывал реальную усталость, объём и заметки."
                ),
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
                    workoutCopy(
                        "Saved. AI can use this workout result next time.",
                        "Сохранено. AI сможет учесть этот результат в следующий раз."
                    ),
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
private fun exerciseLabel(exerciseId: String): String {
    val resource = when (normalizeExerciseId(exerciseId)) {
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
private fun exerciseGuide(exerciseId: String): ExerciseGuideData {
    val isRu = Locale.current.language.startsWith("ru")
    return when (normalizeExerciseId(exerciseId)) {
        "squat", "lunge", "leg_press", "hip_thrust" -> guide(
            illustration = Res.drawable.workout_illustration_lower_body,
            icon = Icons.Filled.DirectionsRun,
            descriptionEn = "Lower-body strength work for quads, glutes, and a stable torso.",
            descriptionRu = "Силовая работа на ноги и ягодицы с акцентом на стабильный корпус.",
            focusEn = listOf("Legs", "Glutes", "Core"),
            focusRu = listOf("Ноги", "Ягодицы", "Кор"),
            cueEn = "Keep your whole foot grounded and let the knees track naturally over the toes.",
            cueRu = "Держи опору на всей стопе и веди колени естественно по линии носков.",
            stepsEn = listOf(
                "Brace your torso before every rep.",
                "Lower with control instead of dropping into the bottom.",
                "Drive up smoothly without losing balance."
            ),
            stepsRu = listOf(
                "Собери корпус перед каждым повтором.",
                "Опускайся под контролем, не падая в нижнюю точку.",
                "Поднимайся плавно, не теряя баланс."
            ),
            restSeconds = 90,
            isRu = isRu
        )
        "bench", "pushup", "dip" -> guide(
            illustration = Res.drawable.workout_illustration_push,
            icon = Icons.Filled.FitnessCenter,
            descriptionEn = "Pressing pattern for chest, shoulders, and triceps with controlled range of motion.",
            descriptionRu = "Жимовой паттерн для груди, плеч и трицепса с контролируемой амплитудой.",
            focusEn = listOf("Chest", "Triceps", "Shoulders"),
            focusRu = listOf("Грудь", "Трицепс", "Плечи"),
            cueEn = "Set the shoulders first and press from a stable torso instead of shrugging.",
            cueRu = "Сначала собери плечи и жми из стабильного корпуса, а не за счёт подъёма плеч.",
            stepsEn = listOf(
                "Fix the shoulder position before the first rep.",
                "Lower under control and keep elbows predictable.",
                "Press back up without bouncing or collapsing."
            ),
            stepsRu = listOf(
                "Зафиксируй положение плеч до первого повтора.",
                "Опускайся под контролем и держи локти предсказуемо.",
                "Выжимай вверх без отбива и провала корпуса."
            ),
            restSeconds = 75,
            isRu = isRu
        )
        "deadlift", "row", "pullup" -> guide(
            illustration = Res.drawable.workout_illustration_pull,
            icon = Icons.Filled.FitnessCenter,
            descriptionEn = "Pulling work for back strength, posture, and grip quality.",
            descriptionRu = "Тяговая работа для силы спины, осанки и качественного хвата.",
            focusEn = listOf("Back", "Lats", "Grip"),
            focusRu = listOf("Спина", "Широчайшие", "Хват"),
            cueEn = "Keep the load close and move from a braced torso, not from the neck.",
            cueRu = "Держи нагрузку ближе к телу и работай из жёсткого корпуса, а не шеей.",
            stepsEn = listOf(
                "Find a solid start position and set your back.",
                "Pull with the elbows while keeping the ribcage controlled.",
                "Return the weight smoothly instead of dropping it."
            ),
            stepsRu = listOf(
                "Найди устойчивый старт и собери спину.",
                "Тяни локтями, сохраняя контроль над рёбрами.",
                "Возвращай вес плавно, а не бросай его."
            ),
            restSeconds = 105,
            isRu = isRu
        )
        "ohp" -> guide(
            illustration = Res.drawable.workout_illustration_overhead,
            icon = Icons.Filled.FitnessCenter,
            descriptionEn = "Vertical press for stronger shoulders and a more stable trunk.",
            descriptionRu = "Вертикальный жим для более сильных плеч и устойчивого корпуса.",
            focusEn = listOf("Shoulders", "Triceps", "Core"),
            focusRu = listOf("Плечи", "Трицепс", "Кор"),
            cueEn = "Squeeze glutes, keep ribs down, and finish with the load stacked over the body.",
            cueRu = "Сожми ягодицы, держи рёбра собранными и фиксируй вес строго над телом.",
            stepsEn = listOf(
                "Set the ribcage and glutes before the press.",
                "Drive the weight straight up without leaning back.",
                "Finish with the head through and elbows locked under control."
            ),
            stepsRu = listOf(
                "Перед жимом собери рёбра и ягодицы.",
                "Веди вес строго вверх, не заваливаясь назад.",
                "В верхней точке выведи голову вперёд и зафиксируй локти под контролем."
            ),
            restSeconds = 90,
            isRu = isRu
        )
        "curl", "tricep_extension" -> guide(
            illustration = Res.drawable.workout_illustration_arms,
            icon = Icons.Filled.FitnessCenter,
            descriptionEn = "Accessory arm work to build elbow control and cleaner lockout.",
            descriptionRu = "Изолирующая работа на руки для контроля локтя и более чистой фиксации.",
            focusEn = listOf("Arms", "Control"),
            focusRu = listOf("Руки", "Контроль"),
            cueEn = "Move from the elbow and avoid turning the set into a body swing.",
            cueRu = "Двигайся из локтя и не превращай подход в раскачку корпусом.",
            stepsEn = listOf(
                "Fix the upper arm before you start.",
                "Use a steady tempo in both directions.",
                "Pause briefly where the muscle works hardest."
            ),
            stepsRu = listOf(
                "Зафиксируй плечо перед началом.",
                "Держи ровный темп в обе стороны.",
                "Сделай короткую паузу в точке максимального напряжения."
            ),
            restSeconds = 60,
            isRu = isRu
        )
        "plank", "yoga" -> guide(
            illustration = Res.drawable.workout_illustration_core,
            icon = Icons.Filled.SelfImprovement,
            descriptionEn = "Recovery and trunk control work focused on breathing, alignment, and quality tension.",
            descriptionRu = "Работа на восстановление и контроль корпуса с акцентом на дыхание, выравнивание и качественное напряжение.",
            focusEn = listOf("Core", "Breathing", "Recovery"),
            focusRu = listOf("Кор", "Дыхание", "Восстановление"),
            cueEn = "Keep the breath calm and stop before posture collapses.",
            cueRu = "Держи спокойное дыхание и заканчивай подход до потери позиции.",
            stepsEn = listOf(
                "Set the position carefully before loading it.",
                "Breathe slowly and keep the neck relaxed.",
                "Maintain tension only while form stays clean."
            ),
            stepsRu = listOf(
                "Точно выставь позицию до начала нагрузки.",
                "Дыши медленно и держи шею расслабленной.",
                "Сохраняй напряжение только пока форма остаётся чистой."
            ),
            restSeconds = 45,
            isRu = isRu
        )
        "run", "bike" -> guide(
            illustration = Res.drawable.workout_illustration_cardio,
            icon = if (normalizeExerciseId(exerciseId) == "bike") Icons.Filled.DirectionsBike else Icons.Filled.DirectionsRun,
            descriptionEn = "Cardio interval focused on rhythm, pacing, and repeatable effort.",
            descriptionRu = "Кардио-интервал с акцентом на ритм, темп и повторяемое усилие.",
            focusEn = listOf("Cardio", "Pacing"),
            focusRu = listOf("Кардио", "Темп"),
            cueEn = "Work hard enough to feel effort but smooth enough to keep form stable.",
            cueRu = "Работай достаточно интенсивно, чтобы чувствовать нагрузку, но достаточно ровно, чтобы техника не разваливалась.",
            stepsEn = listOf(
                "Build speed or resistance gradually.",
                "Keep shoulders and jaw relaxed.",
                "Finish the interval under control instead of sprinting blindly."
            ),
            stepsRu = listOf(
                "Постепенно набирай скорость или сопротивление.",
                "Держи плечи и челюсть расслабленными.",
                "Завершай интервал под контролем, а не вслепую на максимуме."
            ),
            restSeconds = 60,
            isRu = isRu
        )
        else -> guide(
            illustration = Res.drawable.workout_illustration_generic,
            icon = Icons.Filled.FitnessCenter,
            descriptionEn = "Controlled strength work with focus on repeat quality and technique stability.",
            descriptionRu = "Контролируемая силовая работа с акцентом на качество повторений и устойчивую технику.",
            focusEn = listOf("Strength"),
            focusRu = listOf("Сила"),
            cueEn = "Move smoothly and stop the set before technique breaks down.",
            cueRu = "Двигайся плавно и заканчивай подход до того, как техника начнёт ломаться.",
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
            restSeconds = 75,
            isRu = isRu
        )
    }
}

private fun guide(
    illustration: DrawableResource,
    icon: ImageVector,
    descriptionEn: String,
    descriptionRu: String,
    focusEn: List<String>,
    focusRu: List<String>,
    cueEn: String,
    cueRu: String,
    stepsEn: List<String>,
    stepsRu: List<String>,
    restSeconds: Int,
    isRu: Boolean
): ExerciseGuideData = ExerciseGuideData(
    illustration = illustration,
    icon = icon,
    description = if (isRu) descriptionRu else descriptionEn,
    focus = if (isRu) focusRu else focusEn,
    cue = if (isRu) cueRu else cueEn,
    steps = if (isRu) stepsRu else stepsEn,
    defaultRestSeconds = restSeconds
)

@Composable
private fun plannedSetSummary(set: WorkoutSet): String {
    val weight = set.weightKg?.let {
        stringResource(Res.string.workout_weight_display).kmpFormat(it)
    } ?: stringResource(Res.string.workout_weight_bodyweight)
    val rpe = set.rpe?.let { " · RPE ${it.toEditableDecimal()}" }.orEmpty()
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
private fun workoutCopy(en: String, ru: String): String =
    if (Locale.current.language.startsWith("ru")) ru else en

@Composable
private fun workoutCardColors() =
    CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.97f))

@Composable
private fun workoutCardElevation() =
    CardDefaults.cardElevation(defaultElevation = 8.dp)
