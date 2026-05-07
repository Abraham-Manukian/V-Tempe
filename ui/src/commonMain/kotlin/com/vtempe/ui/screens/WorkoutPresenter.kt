package com.vtempe.ui.screens

import androidx.compose.runtime.Composable
import com.vtempe.ui.presenter.WorkoutPresenter

@Composable
expect fun rememberWorkoutPresenter(): WorkoutPresenter
