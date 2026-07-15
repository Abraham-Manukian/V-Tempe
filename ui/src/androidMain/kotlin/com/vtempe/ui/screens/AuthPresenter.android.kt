package com.vtempe.ui.screens

import androidx.compose.runtime.Composable
import com.vtempe.ui.presenter.AuthPresenter
import com.vtempe.ui.vm.AuthViewModel
import org.koin.androidx.compose.koinViewModel

@Composable
actual fun rememberAuthPresenter(): AuthPresenter = koinViewModel<AuthViewModel>()
