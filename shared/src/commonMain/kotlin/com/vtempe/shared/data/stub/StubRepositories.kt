package com.vtempe.shared.data.stub

import com.vtempe.shared.domain.model.Advice
import com.vtempe.shared.domain.model.Profile
import com.vtempe.shared.domain.repository.AdviceRepository
import com.vtempe.shared.domain.repository.AnalyticsRepository
import com.vtempe.shared.domain.repository.PurchasesRepository
import com.vtempe.shared.domain.repository.SyncRepository
import io.github.aakira.napier.Napier
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
        true  // Stub always has default advice — prevents bootstrap on every launch

    private fun defaultAdvice(topic: String): Advice = Advice(messages = emptyList())
}

/**
 * Alpha / free tier: subscription always active.
 * Replace with AndroidPurchasesRepository (Google Play Billing) before paid launch.
 */
class StubPurchasesRepository : PurchasesRepository {
    override suspend fun isSubscriptionActive(): Boolean = true
}

class StubSyncRepository : SyncRepository {
    override suspend fun syncAll(): Boolean = true
}

/**
 * No-op analytics used on platforms/builds without a wired backend (iOS, or Android
 * without google-services.json). Logs to Napier so events are still visible in dev builds.
 * Replace with FirebaseAnalyticsRepository once google-services.json is added.
 */
class NoOpAnalyticsRepository : AnalyticsRepository {
    override fun logEvent(name: String, params: Map<String, String>) {
        Napier.d(tag = "Analytics", message = "event=$name params=$params")
    }

    override fun setUserProperty(key: String, value: String?) {
        Napier.d(tag = "Analytics", message = "userProperty $key=$value")
    }

    override fun recordNonFatal(throwable: Throwable, message: String?) {
        Napier.e(tag = "Analytics", message = message ?: "non-fatal", throwable = throwable)
    }
}
