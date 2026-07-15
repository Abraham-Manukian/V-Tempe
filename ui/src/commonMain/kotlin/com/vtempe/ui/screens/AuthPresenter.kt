package com.vtempe.ui.screens

import androidx.compose.runtime.Composable
import com.vtempe.ui.presenter.AuthPresenter

@Composable
expect fun rememberAuthPresenter(): AuthPresenter
