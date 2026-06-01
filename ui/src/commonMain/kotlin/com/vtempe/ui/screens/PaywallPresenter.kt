package com.vtempe.ui.screens

import androidx.compose.runtime.Composable
import com.vtempe.ui.presenter.PaywallPresenter

@Composable
expect fun rememberPaywallPresenter(): PaywallPresenter
