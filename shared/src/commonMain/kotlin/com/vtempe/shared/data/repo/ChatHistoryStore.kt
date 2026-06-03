package com.vtempe.shared.data.repo

import com.russhwolf.settings.Settings
import com.vtempe.shared.domain.repository.ChatMessage
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Persists conversation history between app sessions.
 * Keeps the most recent [MAX_MESSAGES] messages so the coach "remembers"
 * previous interactions when the user returns.
 */
class ChatHistoryStore(private val settings: Settings) {

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    @Serializable
    private data class StoredMessage(val role: String, val content: String)

    private val listSerializer = ListSerializer(StoredMessage.serializer())

    /** Load stored messages, or empty list if nothing saved yet. */
    fun load(): List<ChatMessage> =
        settings.getStringOrNull(KEY)
            ?.let { raw ->
                runCatching {
                    json.decodeFromString(listSerializer, raw)
                        .map { ChatMessage(role = it.role, content = it.content) }
                }.getOrDefault(emptyList())
            } ?: emptyList()

    /** Persist [messages], trimming to the most recent [MAX_MESSAGES] entries. */
    fun save(messages: List<ChatMessage>) {
        val stored = messages.takeLast(MAX_MESSAGES)
            .map { StoredMessage(it.role, it.content) }
        settings.putString(KEY, json.encodeToString(listSerializer, stored))
    }

    /** Wipe the chat history (e.g. on full account reset). */
    fun clear() = settings.remove(KEY)

    private companion object {
        const val KEY = "chat.history.v1"
        const val MAX_MESSAGES = 30
    }
}
