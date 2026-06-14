package com.vtempe.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import com.vtempe.core.designsystem.theme.AiTypography
import com.vtempe.core.designsystem.theme.AiShapes
import com.vtempe.core.designsystem.theme.aiDarkColorScheme
import com.vtempe.core.designsystem.theme.aiLightColorScheme

@Composable
fun VTempeTheme(
    darkTheme: Boolean = false,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = platformColorScheme(darkTheme, dynamicColor)
        ?: if (darkTheme) aiDarkColorScheme() else aiLightColorScheme()

    MaterialTheme(
        colorScheme = colorScheme,
        typography = AiTypography(),
        shapes = AiShapes,
        content = content
    )
}

@Composable
internal expect fun platformColorScheme(
    darkTheme: Boolean,
    dynamicColor: Boolean
): ColorScheme?


