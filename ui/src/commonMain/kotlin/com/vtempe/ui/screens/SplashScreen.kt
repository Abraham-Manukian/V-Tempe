package com.vtempe.ui.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.unit.dp
import com.vtempe.core.designsystem.theme.AiGradients
import com.vtempe.core.designsystem.theme.AiPalette

@Composable
fun SplashScreen(onReady: (String) -> Unit = {}) {
    val scale = remember { Animatable(0.8f) }
    val decided: MutableState<Boolean> = remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        scale.animateTo(1f, animationSpec = tween(600))
        val destination = determineStartDestination()
        onReady(destination)
        decided.value = true
    }
    if (!decided.value) {
        Box(
            Modifier
                .fillMaxSize()
                .background(AiGradients.lavenderMist()),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator(
                modifier = Modifier
                    .size(42.dp)
                    .scale(scale.value),
                color = AiPalette.OnGradient
            )
        }
    }
}

expect suspend fun determineStartDestination(): String
