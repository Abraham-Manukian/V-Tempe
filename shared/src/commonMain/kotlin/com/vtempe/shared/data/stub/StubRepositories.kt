package com.vtempe.shared.data.stub

import com.vtempe.shared.domain.model.Advice
import com.vtempe.shared.domain.model.Profile
import com.vtempe.shared.domain.repository.AdviceRepository
import com.vtempe.shared.domain.repository.PurchasesRepository
import com.vtempe.shared.domain.repository.SyncRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/**
 * Offline-first stubs used until real platform implementations are wired in.
 *
 * PurchasesRepository  → replace with AndroidPurchasesRepository / SKProductsStore
 * SyncRepository       → replace with real backend sync when ready
 * AdviceRepository     → replace with NetworkAdviceRepository when advice endpoint ships
 */

class StubAdviceRepository : AdviceRepository {
    private val adviceMap = MutableStateFlow<Map<String, Advice>>(emptyMap())

    override suspend fun getAdvice(profile: Profile, context: Map<String, Any?>): Advice {
        val topic = (context["topic"] as? String)?.lowercase() ?: "general"
        val current = adviceMap.value[topic]
        if (current != null) return current
        val defaults = defaultAdvice(topic)
        adviceMap.value = adviceMap.value + (topic to defaults)
        return defaults
    }

    override suspend fun saveAdvice(topic: String, advice: Advice) {
        adviceMap.value = adviceMap.value + (topic.lowercase() to advice)
    }

    override fun observeAdvice(topic: String): Flow<Advice> =
        adviceMap.map { it[topic.lowercase()] ?: defaultAdvice(topic.lowercase()) }

    override suspend fun hasAdvice(topic: String): Boolean =
        adviceMap.value.containsKey(topic.lowercase())

    private fun defaultAdvice(topic: String): Advice = when (topic) {
        "sleep" -> Advice(
            messages = listOf(
                "Sleep 7-9 hours when possible.",
                "Keep a consistent bedtime routine.",
                "Limit caffeine six hours before bed."
            )
        )
        else -> Advice(messages = listOf("Stay hydrated", "Warm up properly"))
    }
}

class StubPurchasesRepository : PurchasesRepository {
    override suspend fun isSubscriptionActive(): Boolean = false
}

class StubSyncRepository : SyncRepository {
    override suspend fun syncAll(): Boolean = true
}
