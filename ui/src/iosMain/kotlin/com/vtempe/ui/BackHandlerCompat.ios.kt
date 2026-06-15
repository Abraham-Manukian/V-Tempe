package com.vtempe.ui

import androidx.compose.runtime.Composable

@Composable
actual fun BackHandlerCompat(enabled: Boolean, onBack: () -> Unit) {
    // iOS handles back navigation natively via UINavigationController
}
