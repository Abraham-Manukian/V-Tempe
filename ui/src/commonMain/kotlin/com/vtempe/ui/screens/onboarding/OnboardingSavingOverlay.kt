@file:OptIn(
    org.jetbrains.compose.resources.ExperimentalResourceApi::class,
    androidx.compose.foundation.layout.ExperimentalLayoutApi::class,
    androidx.compose.material3.ExperimentalMaterial3Api::class
)

package com.vtempe.ui.screens.onboarding

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vtempe.core.designsystem.theme.AiPalette
import com.vtempe.ui.*
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun SavingOverlay(savingStep: Int) {
    val subMessages = listOf(
        stringResource(Res.string.saving_sub_analyze),
        stringResource(Res.string.saving_sub_workouts),
        stringResource(Res.string.saving_sub_exercises),
        stringResource(Res.string.saving_sub_nutrition),
        stringResource(Res.string.saving_sub_sleep),
        stringResource(Res.string.saving_sub_finishing)
    )
    var subMsgIdx by remember { mutableIntStateOf(0) }
    LaunchedEffect(savingStep) {
        if (savingStep >= 1) {
            while (true) {
                delay(2000)
                subMsgIdx = (subMsgIdx + 1) % subMessages.size
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.65f)),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(28.dp),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(defaultElevation = 12.dp)
        ) {
            Column(
                modifier = Modifier.padding(28.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                Text(text = "🤖", fontSize = 52.sp)

                Text(
                    text = stringResource(Res.string.saving_overlay_title),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = onCard,
                    textAlign = TextAlign.Center
                )

                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    SavingStepRow(
                        done = savingStep >= 1,
                        active = savingStep == 0,
                        text = stringResource(Res.string.saving_step_profile)
                    )
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        SavingStepRow(
                            done = false,
                            active = savingStep >= 1,
                            text = stringResource(Res.string.saving_step_plan)
                        )
                        if (savingStep >= 1) {
                            AnimatedContent(
                                targetState = subMsgIdx,
                                modifier = Modifier.padding(start = 36.dp),
                                transitionSpec = { fadeIn() togetherWith fadeOut() }
                            ) { idx ->
                                Text(
                                    text = subMessages[idx % subMessages.size],
                                    style = MaterialTheme.typography.bodySmall,
                                    color = onCard.copy(alpha = 0.5f)
                                )
                            }
                        }
                    }
                }

                LinearProgressIndicator(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(4.dp)),
                    color = AiPalette.Primary,
                    trackColor = AiPalette.Primary.copy(alpha = 0.2f)
                )
            }
        }
    }
}

@Composable
internal fun SavingStepRow(done: Boolean, active: Boolean, text: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        when {
            done -> Icon(
                imageVector = Icons.Filled.CheckCircle,
                contentDescription = null,
                tint = Color(0xFF4CAF50),
                modifier = Modifier.size(24.dp)
            )
            active -> CircularProgressIndicator(
                modifier = Modifier.size(22.dp),
                strokeWidth = 2.5.dp,
                color = AiPalette.Primary
            )
            else -> Box(
                modifier = Modifier
                    .size(22.dp)
                    .border(1.5.dp, onCard.copy(alpha = 0.2f), CircleShape)
            )
        }
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (active || done) FontWeight.SemiBold else FontWeight.Normal,
            color = if (active || done) onCard else onCard.copy(alpha = 0.4f)
        )
    }
}
