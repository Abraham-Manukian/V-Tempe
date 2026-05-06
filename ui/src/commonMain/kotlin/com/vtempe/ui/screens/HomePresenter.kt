package com.vtempe.ui.screens

import androidx.compose.runtime.Composable
import com.vtempe.shared.domain.model.Workout
import kotlinx.coroutines.flow.StateFlow

data class HomeState(
    val workouts: List<Workout> = emptyList(),
    val todaySets: Int = 0,
    val totalVolume: Int = 0,
    val sleepMinutes: Int = 0,
    val loading: Boolean = false,
)

interface HomePresenter {
    val state: StateFlow<HomeState>
}

@Composable
expect fun rememberHomePresenter(): HomePresenter

