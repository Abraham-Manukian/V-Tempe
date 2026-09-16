@file:OptIn(org.jetbrains.compose.resources.ExperimentalResourceApi::class)

package com.vtempe.ui.screens.chat

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.vtempe.core.designsystem.theme.AiPalette
import com.vtempe.shared.domain.repository.ChatMessage
import com.vtempe.ui.*
import com.vtempe.ui.coach.coachAvatarFor
import com.vtempe.ui.navigation.Destination
import com.vtempe.ui.util.MarkdownText
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun EmptyConversationCard() {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = Color.White.copy(alpha = 0.84f),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.36f))
    ) {
        Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
            Text(
                text = stringResource(Res.string.chat_empty_title),
                color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.titleSmall
            )
            Spacer(Modifier.size(6.dp))
            Text(
                text = stringResource(Res.string.chat_empty_body),
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

@Composable
internal fun MessageBubble(msg: ChatMessage, coachTrainerId: String) {
    val isUser = msg.role == "user"
    val roleTitle = if (isUser) {
        stringResource(Res.string.chat_role_you)
    } else {
        stringResource(Res.string.chat_role_coach)
    }

    val bubbleShape = if (isUser) {
        RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp, bottomStart = 20.dp, bottomEnd = 8.dp)
    } else {
        RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp, bottomStart = 8.dp, bottomEnd = 20.dp)
    }

    val userGradient = Brush.horizontalGradient(
        listOf(
            AiPalette.DeepAccent,
            AiPalette.Primary
        )
    )

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = if (isUser) Alignment.End else Alignment.Start
    ) {
        Text(
            text = roleTitle,
            style = MaterialTheme.typography.labelSmall,
            color = Color.White.copy(alpha = 0.88f),
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
        )

        Row(verticalAlignment = Alignment.CenterVertically) {
            if (!isUser) {
                Image(
                    painter = painterResource(coachAvatarFor(coachTrainerId)),
                    contentDescription = roleTitle,
                    modifier = Modifier
                        .size(28.dp)
                        .background(
                            brush = Brush.radialGradient(listOf(AiPalette.Primary, AiPalette.DeepAccent)),
                            shape = CircleShape
                        )
                        .clip(CircleShape),
                    contentScale = ContentScale.Crop,
                    alignment = Alignment.TopCenter
                )
                Spacer(Modifier.size(8.dp))
            }

            if (isUser) {
                Box(
                    modifier = Modifier
                        .widthIn(max = 300.dp)
                        .defaultMinSize(minWidth = 88.dp)
                        .background(brush = userGradient, shape = bubbleShape)
                        .padding(horizontal = 14.dp, vertical = 10.dp)
                ) {
                    Text(
                        text = msg.content,
                        color = Color.White,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            lineHeightStyle = LineHeightStyle.Default
                        ),
                        textAlign = TextAlign.Start
                    )
                }
            } else {
                Surface(
                    modifier = Modifier
                        .widthIn(max = 300.dp)
                        .defaultMinSize(minWidth = 92.dp),
                    shape = bubbleShape,
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.28f))
                ) {
                    MarkdownText(
                        text = msg.content,
                        color = MaterialTheme.colorScheme.onSurface,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            lineHeightStyle = LineHeightStyle.Default
                        ),
                        textAlign = TextAlign.Start,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
                    )
                }
            }
        }
    }
}

@Composable
internal fun DayChip() {
    Surface(
        modifier = Modifier,
        shape = RoundedCornerShape(999.dp),
        color = Color.White.copy(alpha = 0.20f),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.25f))
    ) {
        Text(
            text = stringResource(Res.string.chat_today),
            style = MaterialTheme.typography.labelLarge,
            color = Color.White,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp)
        )
    }
}

/** Broad prompts to kick off a fresh conversation. */
internal val starterSuggestions = listOf(
    Res.string.chat_sg_today_workout,
    Res.string.chat_sg_home_plan,
    Res.string.chat_sg_week_plan,
    Res.string.chat_sg_eat_today,
    Res.string.chat_sg_lose_weight,
    Res.string.chat_sg_gain_muscle,
)

/** Follow-up prompts once the coach is already in a conversation. */
internal val followUpSuggestions = listOf(
    Res.string.chat_sg_swap_exercise,
    Res.string.chat_sg_easier,
    Res.string.chat_sg_add_cardio,
    Res.string.chat_sg_more_protein,
    Res.string.chat_sg_less_calories,
    Res.string.chat_sg_why_exercise,
)

@Composable
internal fun PlanChangeCard(
    changeType: String,
    onNavigate: (Destination) -> Unit
) {
    val (icon, labelRes, destination) = when (changeType) {
        "nutrition" -> Triple("🥗", Res.string.chat_change_nutrition, Destination.Nutrition)
        "sleep"     -> Triple("😴", Res.string.chat_change_sleep,     Destination.Sleep)
        else        -> Triple("💪", Res.string.chat_change_training,  Destination.Workout)
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp)
            .clickable { onNavigate(destination) },
        shape = RoundedCornerShape(16.dp),
        color = Color.White.copy(alpha = 0.12f),
        border = BorderStroke(
            width = 1.dp,
            brush = Brush.horizontalGradient(listOf(AiPalette.Primary, AiPalette.DeepAccent))
        )
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = icon, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.size(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(labelRes),
                    style = MaterialTheme.typography.labelLarge,
                    color = Color.White
                )
                Text(
                    text = stringResource(Res.string.chat_change_tap),
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.65f)
                )
            }
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = null,
                tint = AiPalette.Primary,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

@Composable
internal fun ErrorBubble(errorMessage: String) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = Color.White.copy(alpha = 0.84f),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.36f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.Top
        ) {
            Icon(
                imageVector = Icons.Filled.ErrorOutline,
                contentDescription = null,
                tint = AiPalette.DeepAccent,
                modifier = Modifier
                    .padding(top = 2.dp)
                    .size(16.dp)
            )
            Spacer(Modifier.size(8.dp))
            Text(
                text = errorMessage.ifBlank { stringResource(Res.string.chat_error_unavailable) },
                color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}
