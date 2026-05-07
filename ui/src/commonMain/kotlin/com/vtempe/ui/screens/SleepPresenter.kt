package com.vtempe.ui.screens

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import kotlinx.coroutines.flow.StateFlow

@Immutable
data class SleepState(
    val tips: List<String> = emptyList(),
    val weeklyHours: List<Int> = emptyList(),
    val syncing: Boolean = false,
    val disclaimer: String? = "Not medical advice"
)

interface SleepPresenter {
    val state: StateFlow<SleepState>
    fun sync()
}

@Composable
expect fun rememberSleepPresenter(): SleepPresenter

