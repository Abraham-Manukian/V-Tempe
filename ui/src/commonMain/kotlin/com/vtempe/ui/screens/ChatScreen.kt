@file:OptIn(org.jetbrains.compose.resources.ExperimentalResourceApi::class)

package com.vtempe.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.vtempe.core.designsystem.components.BrandScreen
import com.vtempe.ui.*
import com.vtempe.ui.navigation.Destination
import com.vtempe.ui.presenter.ChatPresenter
import com.vtempe.ui.presenter.ChatSendState
import com.vtempe.ui.screens.chat.*
import kotlinx.coroutines.launch

@Composable
fun ChatScreen(
    initialPrompt: String? = null,
    onPromptConsumed: () -> Unit = {},
    onNavigate: (Destination) -> Unit = {},
    presenter: ChatPresenter = rememberChatPresenter()
) {
    val state by presenter.state.collectAsState()
    val topBarHeight = LocalTopBarHeight.current
    val bottomBarHeight = LocalBottomBarHeight.current
    val isLoading = state.sendState is ChatSendState.Loading
    val errorMessage = (state.sendState as? ChatSendState.Error)?.message

    LaunchedEffect(initialPrompt) {
        val prompt = initialPrompt?.trim().orEmpty()
        if (prompt.isNotBlank()) {
            presenter.updateInput(prompt)
            onPromptConsumed()
        }
    }

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

                val listState = rememberLazyListState()
                val scrollScope = rememberCoroutineScope()
                // Land at the newest message on entering the chat and whenever a new one arrives.
                LaunchedEffect(state.messages.size) {
                    if (state.messages.isNotEmpty()) listState.scrollToItem(state.messages.size - 1)
                }

                Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .fillMaxSize()
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
                            if (msg.role == "plan_change") {
                                PlanChangeCard(changeType = msg.content, onNavigate = onNavigate)
                            } else {
                                MessageBubble(msg = msg, coachTrainerId = state.coachTrainerId)
                            }
                        }

                        if (errorMessage != null) {
                            item {
                                ErrorBubble(errorMessage = errorMessage)
                            }
                        }
                    }

                    // Glass "jump to latest" button — appears only when scrolled up away from the end.
                    if (listState.canScrollForward) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .padding(12.dp)
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(Color.White.copy(alpha = 0.18f))
                                .border(BorderStroke(1.dp, Color.White.copy(alpha = 0.35f)), CircleShape)
                                .clickable {
                                    scrollScope.launch {
                                        listState.animateScrollToItem((state.messages.size - 1).coerceAtLeast(0))
                                    }
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Filled.KeyboardArrowDown,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                }

                // Context-aware, varied suggestions: broad "starter" prompts before the chat has
                // any messages, follow-up prompts once a conversation is going. Shuffled and capped
                // so they differ each time the chat is opened instead of always the same two.
                val suggestions = remember(state.messages.isEmpty()) {
                    val pool = if (state.messages.isEmpty()) starterSuggestions else followUpSuggestions
                    pool.shuffled().take(4)
                }
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.18f),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.20f))
                ) {
                    Row(modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp)) {
                        QuickActionRow(
                            suggestions = suggestions,
                            onPick = { presenter.updateInput(it) }
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
