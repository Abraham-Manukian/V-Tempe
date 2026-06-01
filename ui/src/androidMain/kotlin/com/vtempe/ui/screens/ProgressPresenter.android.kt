package com.vtempe.ui.screens

import androidx.compose.runtime.Composable
import com.vtempe.ui.presenter.ProgressPresenter
import com.vtempe.ui.vm.ProgressViewModel
import org.koin.androidx.compose.koinViewModel

@Composable
actual fun rememberProgressPresenter(): ProgressPresenter = koinViewModel<ProgressViewModel>()
