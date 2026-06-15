package com.vtempe.ui

import androidx.compose.runtime.Composable

@Composable
expect fun BackHandlerCompat(enabled: Boolean = true, onBack: () -> Unit)
