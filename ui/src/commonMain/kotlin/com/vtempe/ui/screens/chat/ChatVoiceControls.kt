@file:OptIn(org.jetbrains.compose.resources.ExperimentalResourceApi::class)

package com.vtempe.ui.screens.chat

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.vtempe.core.designsystem.theme.AiPalette
import com.vtempe.shared.domain.model.MAX_CHAT_MESSAGE_LENGTH
import com.vtempe.ui.*
import com.vtempe.ui.presenter.VoiceInputNotice
import com.vtempe.ui.presenter.VoiceInputState
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun MicButton(enabled: Boolean, onClick: () -> Unit) {
    Box(Modifier.padding(bottom = 2.dp)) {
        CircleIconButton(Icons.Filled.Mic, stringResource(Res.string.chat_voice_start), CircleIconStyle.Tinted, enabled, onClick)
    }
}

/** Replaces the text field while recording: live indicator, partial transcript, Cancel and Done. */
@Composable
internal fun VoiceRecordingBar(
    listening: VoiceInputState.Listening,
    onCancel: () -> Unit,
    onDone: () -> Unit
) {
    var seconds by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(1_000)
            seconds++
        }
    }
    // Read in the draw phase: the pulse redraws the dot without recomposing the bar.
    val pulse = rememberInfiniteTransition(label = "voice-pulse").animateFloat(
        initialValue = 1f,
        targetValue = 0.25f,
        animationSpec = infiniteRepeatable(tween(700), RepeatMode.Reverse),
        label = "voice-pulse-alpha"
    )
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        CircleIconButton(Icons.Filled.Close, stringResource(Res.string.chat_voice_cancel), CircleIconStyle.Neutral, onClick = onCancel)
        Spacer(Modifier.size(8.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(10.dp)
                        .graphicsLayer { alpha = if (listening.finishing) 1f else pulse.value }
                        .background(AiPalette.Danger, CircleShape)
                )
                Spacer(Modifier.size(8.dp))
                Text(
                    text = stringResource(if (listening.finishing) Res.string.chat_voice_finishing else Res.string.chat_voice_listening),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    // Only the status is announced: reading the live words aloud would be dictated back.
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }
                )
                Spacer(Modifier.size(8.dp))
                Text(
                    text = formatElapsed(seconds),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
            }
            Text(
                text = listening.transcript.ifBlank { stringResource(Res.string.chat_voice_on_device) },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = if (listening.transcript.isBlank()) 0.6f else 0.9f),
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
        Spacer(Modifier.size(8.dp))
        if (listening.finishing) {
            Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = AiPalette.Primary)
            }
        } else {
            CircleIconButton(Icons.Filled.Check, stringResource(Res.string.chat_voice_done), CircleIconStyle.Primary, onClick = onDone)
        }
    }
}

private enum class CircleIconStyle { Tinted, Neutral, Primary }

/** 48dp round icon button; the label is read once, as a button. */
@Composable
private fun CircleIconButton(
    icon: ImageVector,
    description: String,
    style: CircleIconStyle,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    val shape = Modifier.size(48.dp).clip(CircleShape)
    val filled = when (style) {
        CircleIconStyle.Primary -> shape.background(Brush.horizontalGradient(listOf(AiPalette.DeepAccent, AiPalette.Primary)))
        CircleIconStyle.Tinted -> shape.background(AiPalette.Primary.copy(alpha = 0.14f))
        CircleIconStyle.Neutral -> shape.background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
    }
    Box(
        modifier = filled
            .alpha(if (enabled) 1f else 0.45f)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = when (style) {
                CircleIconStyle.Primary -> Color.White
                CircleIconStyle.Tinted -> AiPalette.DeepAccent
                CircleIconStyle.Neutral -> MaterialTheme.colorScheme.onSurface
            }
        )
    }
}

@Composable
internal fun VoiceNoticeBanner(
    notice: VoiceInputNotice,
    onOpenSettings: () -> Unit,
    onDismiss: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.32f))
    ) {
        Column(Modifier.padding(start = 14.dp, end = 4.dp, top = 10.dp, bottom = 4.dp)) {
            Text(
                text = noticeText(notice),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(end = 10.dp).semantics { liveRegion = LiveRegionMode.Polite }
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                if (notice == VoiceInputNotice.PERMISSION_BLOCKED) {
                    TextButton(onClick = onOpenSettings) { Text(stringResource(Res.string.chat_voice_open_settings)) }
                }
                TextButton(onClick = onDismiss) { Text(stringResource(Res.string.chat_voice_dismiss)) }
            }
        }
    }
}

@Composable
private fun noticeText(notice: VoiceInputNotice): String = when (notice) {
    VoiceInputNotice.CONSENT_REQUIRED -> stringResource(Res.string.chat_voice_notice_consent)
    VoiceInputNotice.UNAVAILABLE -> stringResource(Res.string.chat_voice_notice_unavailable)
    VoiceInputNotice.PERMISSION_DENIED -> stringResource(Res.string.chat_voice_notice_permission_denied)
    VoiceInputNotice.PERMISSION_BLOCKED -> stringResource(Res.string.chat_voice_notice_permission_blocked)
    VoiceInputNotice.LANGUAGE_UNAVAILABLE -> stringResource(Res.string.chat_voice_notice_language)
    VoiceInputNotice.NO_SPEECH -> stringResource(Res.string.chat_voice_notice_no_speech)
    VoiceInputNotice.FAILED -> stringResource(Res.string.chat_voice_notice_failed)
    VoiceInputNotice.TRUNCATED -> stringResource(Res.string.chat_voice_notice_truncated, MAX_CHAT_MESSAGE_LENGTH)
}

private fun formatElapsed(seconds: Int): String =
    "${seconds / 60}:${(seconds % 60).toString().padStart(2, '0')}"
