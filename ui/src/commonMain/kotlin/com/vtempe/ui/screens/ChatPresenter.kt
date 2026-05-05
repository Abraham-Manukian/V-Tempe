package com.vtempe.ui.screens

import androidx.compose.runtime.Composable
import com.vtempe.shared.domain.model.CoachTrainerIds
import com.vtempe.shared.domain.repository.ChatMessage
import kotlinx.coroutines.flow.StateFlow

sealed interface ChatSendState {
    data object Idle : ChatSendState
    data object Loading : ChatSendState
    data object Success : ChatSendState
    data class Error(val message: String) : ChatSendState
}

data class ChatState(
    val messages: List<ChatMessage> = emptyList(),
    val input: String = "",
    val sendState: ChatSendState = ChatSendState.Idle,
    val coachTrainerId: String = CoachTrainerIds.DEFAULT
)

interface ChatPresenter {
    val state: StateFlow<ChatState>
    fun updateInput(text: String)
    fun send()
}

@Composable
expect fun rememberChatPresenter(): ChatPresenter

