package com.vtempe.ui.screens

import androidx.compose.runtime.Composable
import com.vtempe.ui.presenter.PaywallPresenter
import com.vtempe.ui.vm.PaywallViewModel
import org.koin.androidx.compose.koinViewModel

@Composable
actual fun rememberPaywallPresenter(): PaywallPresenter = koinViewModel<PaywallViewModel>()
