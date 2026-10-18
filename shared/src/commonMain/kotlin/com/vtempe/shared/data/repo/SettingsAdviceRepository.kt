package com.vtempe.shared.data.repo

import com.russhwolf.settings.Settings
import com.vtempe.shared.domain.model.Advice
import com.vtempe.shared.domain.model.Profile
import com.vtempe.shared.domain.repository.AdviceRepository
import io.github.aakira.napier.Napier
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

/**
 * The one stored copy of the coach's advice per topic (VT-AUD-041). Bootstrap, chat edits and the
 * Sleep screen's refresh all write here and the screen observes it, so advice survives restarts
 * and reaches the screen however it was produced. Belongs to the device user: cleared with the
 * rest of their data on account switch or reset.
 */
class SettingsAdviceRepository(private val settings: Settings) : AdviceRepository {

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val serializer = MapSerializer(String.serializer(), Advice.serializer())
    private val stored = MutableStateFlow(load())

    /** The stored advice, or none yet — this never asks the coach. */
    override suspend fun getAdvice(profile: Profile, context: Map<String, Any?>): Advice {
        val topic = (context["topic"] as? String)?.lowercase() ?: "general"
        return stored.value[topic] ?: Advice(messages = emptyList())
    }

    override suspend fun saveAdvice(topic: String, advice: Advice) {
        val updated = stored.value + (topic.lowercase() to advice)
        settings.putString(KEY, json.encodeToString(serializer, updated))
        stored.value = updated
    }

    override fun observeAdvice(topic: String): Flow<Advice> =
        stored.map { it[topic.lowercase()] ?: Advice(messages = emptyList()) }.distinctUntilChanged()

    /** True once advice with at least one tip is stored for [topic]. An empty record (earlier
     *  versions saved one after a failed bootstrap) counts as missing, so it is fetched again. */
    override suspend fun hasAdvice(topic: String): Boolean = stored.value[topic.lowercase()]?.messages?.isNotEmpty() == true

    fun clear() {
        settings.remove(KEY)
        stored.value = emptyMap()
    }

    private fun load(): Map<String, Advice> = settings.getStringOrNull(KEY)?.let { raw ->
        runCatching { json.decodeFromString(serializer, raw) }
            .onFailure { Napier.w("Unreadable stored advice ignored") }
            .getOrNull()
    }.orEmpty()

    private companion object {
        const val KEY = "coach.advice.v1"
    }
}
