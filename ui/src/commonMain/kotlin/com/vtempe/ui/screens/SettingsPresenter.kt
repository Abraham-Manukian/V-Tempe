package com.vtempe.ui.screens

import androidx.compose.runtime.Composable
import com.vtempe.ui.presenter.SettingsPresenter

@Composable
expect fun rememberSettingsPresenter(): SettingsPresenter
