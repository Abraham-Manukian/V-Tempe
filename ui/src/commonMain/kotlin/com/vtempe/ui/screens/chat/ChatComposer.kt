@file:OptIn(org.jetbrains.compose.resources.ExperimentalResourceApi::class)

package com.vtempe.ui.screens.chat

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.vtempe.core.designsystem.theme.AiPalette
import com.vtempe.ui.*
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun QuickActionRow(
    suggestions: List<org.jetbrains.compose.resources.StringResource>,
    onPick: (String) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        suggestions.forEach { res ->
            val label = stringResource(res)
            QuickActionButton(label = label, onClick = { onPick(label) })
        }
    }
}

@Composable
internal fun QuickActionButton(
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
internal fun ComposerBar(
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
            verticalAlignment = Alignment.Bottom
        ) {
            OutlinedTextField(
                value = input,
                onValueChange = onInputChanged,
                modifier = Modifier.weight(1f),
                placeholder = {
                    Text(
                        text = stringResource(Res.string.chat_hint),
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                },
                singleLine = false,
                maxLines = 5,
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

            Box(
                modifier = Modifier
                    .padding(bottom = 4.dp)
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
                            imageVector = Icons.AutoMirrored.Filled.Send,
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
