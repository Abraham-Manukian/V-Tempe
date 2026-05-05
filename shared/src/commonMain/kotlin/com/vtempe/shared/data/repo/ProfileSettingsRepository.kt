package com.vtempe.shared.data.repo

import com.vtempe.shared.domain.model.*
import com.vtempe.shared.domain.repository.ProfileRepository
import com.russhwolf.settings.Settings
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString

abstract class ProfileSettingsRepository(
    private val settings: Settings,
) : ProfileRepository {
    private val key = "profile.json"
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    override suspend fun getProfile(): Profile? {
        val raw = settings.getStringOrNull(key) ?: return null
        return runCatching {
            val p: PersistedProfile = json.decodeFromString(raw)
            p.toDomain()
        }.getOrNull()
    }

    override suspend fun upsertProfile(profile: Profile) {
        val dto = PersistedProfile.fromDomain(profile)
        val raw: String = json.encodeToString(dto)
        settings.putString(key, raw)
    }
}

@Serializable
private data class PersistedProfile(
    val id: String,
    val age: Int,
    val sex: String,
    val heightCm: Int,
    val weightKg: Double,
    val goal: String,
    val experienceLevel: Int,
    val constraints: List<String> = emptyList(),
    val equipment: List<String> = emptyList(),
    val dietaryPreferences: List<String> = emptyList(),
    val allergies: List<String> = emptyList(),
    val weeklySchedule: Map<String, Boolean> = emptyMap(),
    val budgetLevel: Int = 2,
    val trainingMode: String = "AUTO",
    val coachTrainerId: String = CoachTrainerIds.DEFAULT,
) {
    fun toDomain(): Profile = Profile(
        id = id,
        age = age,
        sex = runCatching { Sex.valueOf(sex) }.getOrDefault(Sex.OTHER),
        heightCm = heightCm,
        weightKg = weightKg,
        goal = runCatching { Goal.valueOf(goal) }.getOrDefault(Goal.MAINTAIN),
        experienceLevel = experienceLevel,
        constraints = Constraints(injuries = constraints),
        equipment = Equipment(items = equipment),
        dietaryPreferences = dietaryPreferences,
        allergies = allergies,
        weeklySchedule = weeklySchedule,
        budgetLevel = budgetLevel,
        trainingMode = trainingMode,
        coachTrainerId = CoachTrainerIds.normalize(coachTrainerId)
    )

    companion object {
        fun fromDomain(p: Profile) = PersistedProfile(
            id = p.id,
            age = p.age,
            sex = p.sex.name,
            heightCm = p.heightCm,
            weightKg = p.weightKg,
            goal = p.goal.name,
            experienceLevel = p.experienceLevel,
            constraints = p.constraints.injuries,
            equipment = p.equipment.items,
            dietaryPreferences = p.dietaryPreferences,
            allergies = p.allergies,
            weeklySchedule = p.weeklySchedule,
            budgetLevel = p.budgetLevel,
            trainingMode = p.trainingMode,
            coachTrainerId = CoachTrainerIds.normalize(p.coachTrainerId)
        )
    }
}

