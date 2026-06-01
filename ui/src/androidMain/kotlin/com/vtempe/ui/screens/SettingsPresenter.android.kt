package com.vtempe.ui.screens

import androidx.compose.runtime.Composable
import com.vtempe.ui.presenter.SettingsPresenter
import com.vtempe.ui.vm.SettingsViewModel
import org.koin.androidx.compose.koinViewModel

@Composable
actual fun rememberSettingsPresenter(): SettingsPresenter = koinViewModel<SettingsViewModel>()
