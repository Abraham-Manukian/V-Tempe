package com.vtempe.ui.screens

import androidx.compose.runtime.Composable
import com.vtempe.ui.presenter.HomePresenter
import com.vtempe.ui.vm.HomeViewModel
import org.koin.androidx.compose.koinViewModel

@Composable
actual fun rememberHomePresenter(): HomePresenter = koinViewModel<HomeViewModel>()

