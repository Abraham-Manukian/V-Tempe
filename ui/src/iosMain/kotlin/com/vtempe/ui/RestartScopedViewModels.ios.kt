package com.vtempe.ui

import androidx.compose.runtime.Composable

// iOS presenters are remembered in composition, so the restart key already disposes them.
@Composable
internal actual fun RestartScopedViewModels(generation: Int, content: @Composable () -> Unit) = content()
