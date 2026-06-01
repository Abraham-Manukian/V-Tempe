package com.vtempe.ui.screens

import androidx.compose.runtime.Composable
import com.vtempe.ui.presenter.WorkoutPresenter
import com.vtempe.ui.vm.WorkoutViewModel
import org.koin.androidx.compose.koinViewModel

@Composable
actual fun rememberWorkoutPresenter(): WorkoutPresenter = koinViewModel<WorkoutViewModel>()

