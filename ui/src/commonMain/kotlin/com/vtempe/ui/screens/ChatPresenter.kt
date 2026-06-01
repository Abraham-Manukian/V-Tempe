package com.vtempe.ui.screens

import androidx.compose.runtime.Composable
import com.vtempe.ui.presenter.ChatPresenter

@Composable
expect fun rememberChatPresenter(): ChatPresenter
