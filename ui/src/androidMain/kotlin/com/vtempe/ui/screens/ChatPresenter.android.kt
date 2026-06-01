package com.vtempe.ui.screens

import androidx.compose.runtime.Composable
import com.vtempe.ui.presenter.ChatPresenter
import com.vtempe.ui.vm.ChatViewModel
import org.koin.androidx.compose.koinViewModel

@Composable
actual fun rememberChatPresenter(): ChatPresenter = koinViewModel<ChatViewModel>()

