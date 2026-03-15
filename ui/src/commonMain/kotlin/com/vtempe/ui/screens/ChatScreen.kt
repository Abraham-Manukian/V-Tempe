@file:OptIn(org.jetbrains.compose.resources.ExperimentalResourceApi::class)

package com.vtempe.ui.screens

import com.vtempe.ui.*
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.vtempe.core.designsystem.components.BrandScreen
import com.vtempe.core.designsystem.theme.AiPalette
import com.vtempe.shared.domain.repository.ChatMessage
import org.jetbrains.compose.resources.stringResource

@Composable
fun ChatScreen(
    presenter: ChatPresenter = rememberChatPresenter()
) {
    val state by presenter.state.collectAsState()
    val topBarHeight = LocalTopBarHeight.current
    val bottomBarHeight = LocalBottomBarHeight.current
    val isLoading = state.sendState is ChatSendState.Loading
    val errorMessage = (state.sendState as? ChatSendState.Error)?.message
    val quickShowWorkoutPrompt = stringResource(Res.string.chat_quick_show_workout_prompt)
    val quickHomePlanPrompt = stringResource(Res.string.chat_quick_make_home_plan_prompt)

    BrandScreen(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            Spacer(Modifier.size(topBarHeight + 8.dp))

            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp),
                    contentAlignment = Alignment.Center
                ) {
                    DayChip()
                }

                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(top = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(bottom = 8.dp)
                ) {
                    if (state.messages.isEmpty()) {
                        item {
                            EmptyConversationCard()
                        }
                    }

                    itemsIndexed(state.messages) { _, msg ->
                        AnimatedVisibility(
                            visible = true,
                            enter = fadeIn(animationSpec = tween(220)) + slideInVertically(
                                initialOffsetY = { it / 10 },
                                animationSpec = tween(220)
                            )
                        ) {
                            MessageBubble(msg = msg)
                        }
                    }

                    if (!errorMessage.isNullOrBlank()) {
                        item {
                            ErrorBubble(errorMessage = errorMessage)
                        }
                    }
                }

                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.18f),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.20f))
                ) {
                    Row(modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp)) {
                        QuickActionRow(
                            onShowWorkout = {
                                presenter.updateInput(quickShowWorkoutPrompt)
                            },
                            onHomePlan = {
                                presenter.updateInput(quickHomePlanPrompt)
                            }
                        )
                    }
                }

                Spacer(Modifier.size(10.dp))

                ComposerBar(
                    input = state.input,
                    isLoading = isLoading,
                    onInputChanged = presenter::updateInput,
                    onSend = presenter::send
                )
            }

            Spacer(Modifier.size(bottomBarHeight + 8.dp))
        }
    }
}

@Composable
private fun EmptyConversationCard() {
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
private fun MessageBubble(msg: ChatMessage) {
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
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .background(
                            brush = Brush.radialGradient(listOf(AiPalette.Primary, AiPalette.DeepAccent)),
                            shape = CircleShape
                        )
                ) {
                    Icon(
                        imageVector = Icons.Filled.Star,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier
                            .size(14.dp)
                            .align(Alignment.Center)
                    )
                }
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
                    Text(
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
private fun DayChip() {
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

@Composable
private fun QuickActionRow(
    onShowWorkout: () -> Unit,
    onHomePlan: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        QuickActionButton(
            label = stringResource(Res.string.chat_quick_show_workout),
            onClick = onShowWorkout
        )
        QuickActionButton(
            label = stringResource(Res.string.chat_quick_make_home_plan),
            onClick = onHomePlan
        )
    }
}

@Composable
private fun QuickActionButton(
    label: String,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(999.dp),
        color = Color.White.copy(alpha = 0.78f),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.45f)),
        modifier = Modifier.clickable(onClick = onClick)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = AiPalette.DeepAccent,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
        )
    }
}

@Composable
private fun ComposerBar(
    input: String,
    isLoading: Boolean,
    onInputChanged: (String) -> Unit,
    onSend: () -> Unit
) {
    val canSend = input.isNotBlank() && !isLoading
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.90f),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.32f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = input,
                onValueChange = onInputChanged,
                modifier = Modifier
                    .weight(1f)
                    .height(52.dp),
                placeholder = {
                    Text(
                        text = stringResource(Res.string.chat_hint),
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                },
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Color.Transparent,
                    unfocusedBorderColor = Color.Transparent,
                    disabledBorderColor = Color.Transparent,
                    focusedContainerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.62f),
                    unfocusedContainerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.62f),
                    cursorColor = AiPalette.Primary
                )
            )

            Spacer(Modifier.size(8.dp))

            IconButton(
                onClick = {},
                modifier = Modifier
                    .size(44.dp)
                    .background(
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                        shape = RoundedCornerShape(14.dp)
                    )
            ) {
                Icon(
                    imageVector = Icons.Filled.Mic,
                    contentDescription = null,
                    tint = AiPalette.DeepAccent
                )
            }

            Spacer(Modifier.size(8.dp))

            Box(
                modifier = Modifier
                    .alpha(if (canSend) 1f else 0.45f)
                    .background(
                        brush = Brush.horizontalGradient(
                            listOf(AiPalette.DeepAccent, AiPalette.Primary)
                        ),
                        shape = RoundedCornerShape(16.dp)
                    )
                    .clickable(enabled = canSend, onClick = onSend)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                contentAlignment = Alignment.Center
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = Color.White
                    )
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Filled.Send,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.size(6.dp))
                        Text(
                            text = stringResource(Res.string.chat_send),
                            color = Color.White,
                            style = MaterialTheme.typography.labelLarge
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ErrorBubble(errorMessage: String) {
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
                imageVector = Icons.Filled.Star,
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
