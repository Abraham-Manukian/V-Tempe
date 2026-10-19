package com.vtempe.ui.presenter

import com.vtempe.shared.data.consent.ConsentGatedChatRepository
import com.vtempe.shared.data.repo.ChatHistoryStore
import com.vtempe.shared.data.repo.SettingsAdviceRepository
import com.vtempe.shared.domain.consent.HealthDataConsentGate
import com.vtempe.shared.domain.model.Goal
import com.vtempe.shared.domain.model.NutritionPlan
import com.vtempe.shared.domain.model.Profile
import com.vtempe.shared.domain.model.Sex
import com.vtempe.shared.domain.model.TrainingPlan
import com.vtempe.shared.domain.repository.AnalyticsRepository
import com.vtempe.shared.domain.repository.ChatMessage
import com.vtempe.shared.domain.repository.ChatRepository
import com.vtempe.shared.domain.repository.CoachResponse
import com.vtempe.shared.domain.repository.LanguagePreferences
import com.vtempe.shared.domain.repository.ProfileRepository
import com.vtempe.shared.domain.usecase.AskAiTrainer
import com.vtempe.shared.domain.usecase.MaterializeCoachActions
import com.vtempe.shared.domain.util.DataResult
import com.vtempe.ui.platform.voice.SpeechError
import com.vtempe.ui.platform.voice.SpeechEvent
import com.vtempe.ui.platform.voice.SpeechToText
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.ProducerScope
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.test.TestScope

/** A chat presenter over fake repositories; the AI answers only when the test completes [reply]. */
internal class ChatFixture {
    private val profile = Profile("p", 30, Sex.MALE, 180, 80.0, Goal.MAINTAIN, 2)

    val settings = mutableMapOf<String, String>()
    val history = ChatHistoryStore(memorySettings(settings))
    var profileGate: CompletableDeferred<Unit>? = null
    val sent = mutableListOf<Pair<List<ChatMessage>, String>>()
    val answers = ArrayDeque<CompletableDeferred<DataResult<CoachResponse>>>()
    var throwOnSend: Exception? = null
    var consentGranted = true
    val speech = FakeSpeechToText()
    var locale: String? = null

    private val profiles = object : ProfileRepository {
        override suspend fun getProfile(): Profile { profileGate?.await(); return profile }
        override suspend fun upsertProfile(profile: Profile) = Unit
        override suspend fun clearAll() = Unit
    }
    private val network = object : ChatRepository {
        override suspend fun send(
            profile: Profile, history: List<ChatMessage>, userMessage: String, locale: String?,
            currentTrainingPlan: TrainingPlan?, currentNutritionPlan: NutritionPlan?,
        ): DataResult<CoachResponse> {
            throwOnSend?.let { throw it }
            sent += history to userMessage
            return answers.removeFirst().await()
        }
    }
    /** The production gate in front of the network, so consent is checked on every send. */
    private val chat = ConsentGatedChatRepository(network, HealthDataConsentGate { consentGranted })

    fun presenter(scope: TestScope, presenterScope: CoroutineScope = scope.backgroundScope): ChatPresenterDelegate {
        val training = FakeTraining()
        val nutrition = FakeNutrition()
        val ask = AskAiTrainer(
            profiles, chat,
            object : LanguagePreferences { override fun getLanguageTag() = "en"; override fun setLanguageTag(tag: String?) = Unit },
            MaterializeCoachActions(profiles, training, nutrition, SettingsAdviceRepository(memorySettings())),
            FakeCoachCache(), training, nutrition,
        )
        return ChatPresenterDelegate(
            ask, profiles, history, presenterScope,
            localeProvider = { locale },
            analytics = stub<AnalyticsRepository> { null },
            speechToText = speech,
            healthDataConsent = HealthDataConsentGate { consentGranted }
        )
    }

    fun reply(text: String) = CompletableDeferred<DataResult<CoachResponse>>().also { answers.addLast(it) }.let { it to text }
}

/** Recognizer driven by the test: [say]/[finalResult]/[fail] feed the running [listen]. */
internal class FakeSpeechToText : SpeechToText {
    var available = true
    var listens = 0
    var stops = 0
    var released = 0
    var lastLanguage: String? = null
    var throwOnListen: Exception? = null
    private var active: ProducerScope<SpeechEvent>? = null

    override fun isAvailable(languageTag: String?) = available

    override fun listen(languageTag: String?): Flow<SpeechEvent> = callbackFlow {
        throwOnListen?.let { throw it }
        listens++
        lastLanguage = languageTag
        val session = this
        active = session
        awaitClose {
            released++
            // A newer session may already be running (Cancel, then the mic again).
            if (active === session) active = null
        }
    }

    override fun stopListening() { stops++ }

    val isListening get() = active != null

    fun say(text: String) = emit(SpeechEvent.Partial(text))

    /** Sends [event] without ending the session, like a recognizer that keeps talking. */
    fun emit(event: SpeechEvent) { checkNotNull(active).trySend(event) }

    fun finalResult(text: String) = checkNotNull(active).run { trySend(SpeechEvent.Final(text)); close() }

    fun fail(error: SpeechError) = checkNotNull(active).run { trySend(SpeechEvent.Failed(error)); close() }

    /** The recognizer closes without a final result or an error. */
    fun endSilently() { checkNotNull(active).close() }
}
