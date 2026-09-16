package com.vtempe.ui

import androidx.compose.runtime.Composable

/**
 * Screen presenters created inside [content] belong to one app [generation]: an in-app restart
 * (reset, account switch) must not hand the new UI presenters still holding the old user's data.
 */
@Composable
internal expect fun RestartScopedViewModels(generation: Int, content: @Composable () -> Unit)
