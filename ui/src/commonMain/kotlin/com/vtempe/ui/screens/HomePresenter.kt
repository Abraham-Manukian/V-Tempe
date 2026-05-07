package com.vtempe.ui.screens

import androidx.compose.runtime.Composable
import com.vtempe.ui.presenter.HomePresenter

@Composable
expect fun rememberHomePresenter(): HomePresenter
