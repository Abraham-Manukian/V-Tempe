package com.vtempe.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.vtempe.core.designsystem.theme.AiGradients


@Composable
fun AppBackground() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(AiGradients.brandGradient())
    )
}

@Composable
fun GlassTopBarContainer(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val shape = RoundedCornerShape(24.dp)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(top = 12.dp, start = 16.dp, end = 16.dp)
    ) {
        // Фоновая подложка (Стекло)
        Box(
            modifier = Modifier
                .matchParentSize()
                .shadow(
                    elevation = 20.dp,
                    shape = shape,
                    clip = false,
                    ambientColor = Color.Black.copy(alpha = 0.25f),
                    spotColor = Color.Black.copy(alpha = 0.25f)
                )
                .clip(shape)
                .background(topGlassBrush())
                .border(
                    1.dp,
                    Brush.verticalGradient(
                        listOf(Color.White.copy(alpha = 0.6f), Color.White.copy(alpha = 0.1f))
                    ),
                    shape
                )
        )

        // Контент (Иконки) - остается четким
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp)
        ) {
            content()
        }
    }
}


@Composable
fun GlassBottomBarContainer(content: @Composable () -> Unit) {

    val shape = RoundedCornerShape(32.dp)
    Box(
        modifier = Modifier
            .padding(horizontal = 16.dp)
    ) {
        // Фоновая подложка (Стекло)
        Box(
            modifier = Modifier
                .matchParentSize()
                .shadow(
                    elevation = 24.dp,
                    shape = shape,
                    clip = false,
                    ambientColor = Color.Black.copy(alpha = 0.3f),
                    spotColor = Color.Black.copy(alpha = 0.3f)
                )
                .clip(shape)
                .background(bottomGlassBrush())
                .border(
                    1.dp,
                    Brush.verticalGradient(
                        listOf(Color.White.copy(alpha = 0.5f), Color.White.copy(alpha = 0.1f))
                    ),
                    shape
                )
        )

        // Контент (Табы) - остается четким
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 4.dp)
        ) {
            content()
        }
    }
}

@Composable
private fun topGlassBrush(): Brush {
    val s = MaterialTheme.colorScheme.surface
    return Brush.verticalGradient(
        listOf(
            s.copy(alpha = 0.85f),
            s.copy(alpha = 0.65f)
        )
    )
}

@Composable
private fun bottomGlassBrush(): Brush {
    val s = MaterialTheme.colorScheme.surface
    return Brush.verticalGradient(
        listOf(
            s.copy(alpha = 0.85f),
            s.copy(alpha = 0.65f)
        )
    )
}
