package com.vtempe.server.shared.dto.profile

import kotlinx.serialization.Serializable

@Serializable
data class AiProfile(
    val age: Int,
    val sex: String,
    val heightCm: Int,
    val weightKg: Double,
    val goal: String,
    val experienceLevel: Int,
    val equipment: List<String> = emptyList(),
    val dietaryPreferences: List<String> = emptyList(),
    val allergies: List<String> = emptyList(),
    val injuries: List<String> = emptyList(),
    val healthNotes: List<String> = emptyList(),
    val weeklySchedule: Map<String, Boolean> = emptyMap(),
    val locale: String? = null,
    val budgetLevel: Int? = 2,
    val trainingMode: String = "AUTO",
    val coachTrainerId: String = "mia",
    val llmMode: String? = null,
    val recentWorkouts: List<AiRecentWorkout> = emptyList(),
    val sleepHistory: List<AiSleepEntry> = emptyList(),
    val recentWeights: List<AiWeightEntry> = emptyList()
)

/** One night of logged sleep sent from the client to help the coach adapt recovery advice. */
@Serializable
data class AiSleepEntry(
    val date: String,
    val durationMinutes: Int
)

/** One body-weight measurement sent from the client to help the coach track progress trends. */
@Serializable
data class AiWeightEntry(
    val date: String,
    val weightKg: Double
)
