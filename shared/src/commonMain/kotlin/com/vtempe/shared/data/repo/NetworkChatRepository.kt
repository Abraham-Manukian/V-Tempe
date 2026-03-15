package com.vtempe.shared.data.repo

import com.vtempe.shared.data.network.ApiClient
import com.vtempe.shared.data.network.dto.ChatResponse
import com.vtempe.shared.domain.model.AiModelMode
import com.vtempe.shared.domain.model.Profile
import com.vtempe.shared.domain.repository.ChatMessage
import com.vtempe.shared.domain.repository.ChatRepository
import com.vtempe.shared.domain.repository.CoachResponse
import com.vtempe.shared.domain.repository.PreferencesRepository
import com.vtempe.shared.domain.util.DataResult
import io.github.aakira.napier.Napier
import kotlinx.serialization.Serializable

class NetworkChatRepository(
    private val api: ApiClient,
    private val cache: AiResponseCache,
    private val preferences: PreferencesRepository
) : ChatRepository {
    override suspend fun send(
        profile: Profile,
        history: List<ChatMessage>,
        userMessage: String,
        locale: String?
    ): DataResult<CoachResponse> {
        val request = ChatRequest(
            profile = ChatProfileDto.from(profile, preferences.getAiModelMode()),
            messages = history.map { ChatMsgDto(it.role, it.content) } + ChatMsgDto("user", userMessage),
            locale = locale
        )
        val result = api.postResult<ChatRequest, ChatResponse>("/ai/chat", request)
        return when (result) {
            is DataResult.Success -> {
                cache.storeChatResponse(result.data)
                DataResult.Success(
                    data = result.data.toDomain(),
                    fromCache = result.fromCache,
                    rawPayload = result.rawPayload
                )
            }
            is DataResult.Failure -> {
                cache.lastChatResponse()?.let { cached ->
                    Napier.w("Chat request failed, using cached response", result.throwable)
                    return DataResult.Success(
                        data = cached.toDomain(),
                        fromCache = true,
                        rawPayload = result.rawPayload
                    )
                }
                result
            }
        }
    }
}

@Serializable
data class ChatProfileDto(
    val age: Int,
    val sex: String,
    val heightCm: Int,
    val weightKg: Double,
    val goal: String,
    val experienceLevel: Int,
    val equipment: List<String>,
    val dietaryPreferences: List<String>,
    val allergies: List<String>,
    val weeklySchedule: Map<String, Boolean>,
    val injuries: List<String> = emptyList(),
    val healthNotes: List<String> = emptyList(),
    val budgetLevel: Int = 2,
    val llmMode: String? = null,
) {
    companion object { fun from(p: Profile, llmMode: AiModelMode) = ChatProfileDto(
            age = p.age,
            sex = p.sex.name,
            heightCm = p.heightCm,
            weightKg = p.weightKg,
            goal = p.goal.name,
            experienceLevel = p.experienceLevel,
            equipment = p.equipment.items,
            dietaryPreferences = p.dietaryPreferences,
            allergies = p.allergies,
            weeklySchedule = p.weeklySchedule,
            injuries = p.constraints.injuries,
            healthNotes = p.constraints.healthNotes,
            budgetLevel = p.budgetLevel,
            llmMode = llmMode.wireValue
        )
    }
}

@Serializable
data class ChatMsgDto(val role: String, val content: String)

@Serializable
data class ChatRequest(
    val profile: ChatProfileDto,
    val messages: List<ChatMsgDto>,
    val locale: String? = null
)

