package com.vtempe.ui.screens

import androidx.compose.runtime.Composable
import com.vtempe.ui.presenter.SleepPresenter

@Composable
expect fun rememberSleepPresenter(): SleepPresenter
