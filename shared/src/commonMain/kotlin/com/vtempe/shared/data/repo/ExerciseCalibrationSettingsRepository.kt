package com.vtempe.shared.data.repo

import com.russhwolf.settings.Settings
import com.vtempe.shared.domain.exercise.ExerciseCalibrationRecord
import com.vtempe.shared.domain.repository.ExerciseCalibrationRepository
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

class ExerciseCalibrationSettingsRepository(
    private val settings: Settings
) : ExerciseCalibrationRepository {

    private val key = "exercise.calibrations"
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    override suspend fun get(exerciseId: String): ExerciseCalibrationRecord? =
        readAll().firstOrNull { it.exerciseId == exerciseId }

    override suspend fun list(): List<ExerciseCalibrationRecord> = readAll()

    override suspend fun upsert(record: ExerciseCalibrationRecord) {
        val updated = readAll()
            .filterNot { it.exerciseId == record.exerciseId }
            .plus(record)
            .sortedBy { it.exerciseId }
        writeAll(updated)
    }

    override suspend fun clear(exerciseId: String) {
        writeAll(readAll().filterNot { it.exerciseId == exerciseId })
    }

    override suspend fun clearAll() {
        settings.remove(key)
    }

    private fun readAll(): List<ExerciseCalibrationRecord> {
        val raw = settings.getStringOrNull(key) ?: return emptyList()
        return runCatching {
            json.decodeFromString(ListSerializer(ExerciseCalibrationRecord.serializer()), raw)
        }.getOrDefault(emptyList())
    }

    private fun writeAll(records: List<ExerciseCalibrationRecord>) {
        if (records.isEmpty()) {
            settings.remove(key)
            return
        }
        settings.putString(
            key,
            json.encodeToString(ListSerializer(ExerciseCalibrationRecord.serializer()), records)
        )
    }
}
