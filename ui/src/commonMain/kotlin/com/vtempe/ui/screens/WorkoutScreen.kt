package com.vtempe.ui.screens

import com.vtempe.ui.presenter.WorkoutState
import com.vtempe.ui.presenter.WorkoutPresenter
import com.vtempe.ui.BackHandlerCompat
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
    onNavigateToLibrary: () -> Unit = {},
    onEnterActiveWorkout: () -> Unit = {},
    onExitActiveWorkout: () -> Unit = {},
    presenter: WorkoutPresenter = rememberWorkoutPresenter()
) {
    val state by presenter.state.collectAsState()
    val topBarHeight = LocalTopBarHeight.current
    val bottomBarHeight = LocalBottomBarHeight.current

    // The open workout lives in the presenter, not in this composition (VT-AUD-065).
    val session = state.session
    val detailWorkoutId = session?.workoutId
    val detailWorkout = state.workouts.firstOrNull { it.id == detailWorkoutId }

    // System Back closes the open workout like the on-screen arrow, instead of leaving the app
    // (VT-AUD-064). Registered after the app-level handler, so it takes precedence while open.
    BackHandlerCompat(enabled = detailWorkout != null) { presenter.closeWorkout() }

    LaunchedEffect(Unit) {
        presenter.refresh()
    }

    LaunchedEffect(detailWorkoutId) {
        if (detailWorkoutId != null) onEnterActiveWorkout() else onExitActiveWorkout()
    }

    LaunchedEffect(state.workouts, detailWorkoutId) {
        if (detailWorkoutId != null && detailWorkout == null && state.workouts.isNotEmpty()) presenter.closeWorkout()
    }

    BrandScreen(Modifier.fillMaxSize()) {
        if (detailWorkout == null) {
            WorkoutListContent(
                state = state,
                topBarHeight = topBarHeight,
                bottomBarHeight = bottomBarHeight,
                onOpenWorkout = { presenter.openWorkout(it) },
                onNavigateToLibrary = onNavigateToLibrary
            )
        } else if (session != null) {
            val progress = state.progress[detailWorkout.id] ?: WorkoutProgress(workoutId = detailWorkout.id)
            ActiveWorkoutScreen(
                workout = detailWorkout,
                session = session,
                onStartRest = presenter::startRest,
                onSkipRest = presenter::skipRest,
                progress = progress,
                coachTrainerId = state.coachTrainerId,
                topBarHeight = topBarHeight,
                bottomBarHeight = bottomBarHeight,
                onBack = presenter::closeWorkout,
                onAddSet = { exerciseId, reps, weight, rpe ->
                    presenter.addSet(exerciseId, reps, weight, rpe)
                },
                onUndoSet = { exerciseIndex -> presenter.undoSetDone(detailWorkout.id, exerciseIndex) },
                onSetDone = { exerciseIndex, reps, weight, rpe ->
                    presenter.markSetDone(detailWorkout.id, exerciseIndex, reps, weight, rpe)
                },
                onNotesChanged = { presenter.updateNotes(detailWorkout.id, it) },
                onRestSecondsChanged = { presenter.updateRestSeconds(detailWorkout.id, it) },
                onSubmit = { presenter.submitFeedback(detailWorkout.id) },
                onAskCoach = onAskCoach
            )
        }
    }
}
