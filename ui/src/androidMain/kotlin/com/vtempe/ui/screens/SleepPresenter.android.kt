package com.vtempe.ui.screens

import androidx.compose.runtime.Composable
import com.vtempe.ui.presenter.SleepPresenter
import com.vtempe.ui.vm.SleepViewModel
import org.koin.androidx.compose.koinViewModel

@Composable
actual fun rememberSleepPresenter(): SleepPresenter = koinViewModel<SleepViewModel>()
