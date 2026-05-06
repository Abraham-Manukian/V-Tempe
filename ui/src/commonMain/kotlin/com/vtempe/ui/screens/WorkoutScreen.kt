package com.vtempe.ui.screens

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.vtempe.core.designsystem.components.BrandScreen
import com.vtempe.shared.domain.model.WorkoutProgress
import com.vtempe.ui.LocalBottomBarHeight
import com.vtempe.ui.LocalTopBarHeight

// ─────────────────────────────────────────────────────────────
// Root coordinator
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
