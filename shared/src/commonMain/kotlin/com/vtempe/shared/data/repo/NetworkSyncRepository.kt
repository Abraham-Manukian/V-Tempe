package com.vtempe.shared.data.repo

import com.vtempe.shared.data.network.ApiClient
import com.vtempe.shared.data.network.dto.SyncBlobDto
import com.vtempe.shared.data.network.dto.SyncPullResponseDto
import com.vtempe.shared.data.network.dto.SyncPushRequestDto
import com.vtempe.shared.domain.model.Profile
import com.vtempe.shared.domain.repository.SyncDomain
import com.vtempe.shared.domain.repository.SyncRepository
import com.vtempe.shared.domain.util.DataResult
import io.github.aakira.napier.Napier
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json

class NetworkSyncRepository(
    private val api: ApiClient,
    // Concrete type, not the ProfileRepository interface — restoring a pulled snapshot needs
    // restoreProfile() (skips the onLocalChange hook), which isn't part of that interface.
    private val profileRepository: ProfileRepositoryDb,
    private val workoutProgressStore: WorkoutProgressStore,
    private val sleepStore: SleepStore,
    private val weightStore: WeightStore
) : SyncRepository {

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    // Serializes every push/pull against every other one. Without this, a pullAll() in flight
    // (right after sign-in) can race a pushDomain() fired by a write that happens moments later
    // (e.g. onboarding finishing) — restoreRaw()/restoreProfile() have no version check, so
    // whichever response lands last just wins, and a pull landing after a push would silently
    // discard that fresh local write. Coarse-grained is fine: sync only runs a few times a day
    // per user, never in a hot path.
    private val mutex = Mutex()

    override suspend fun pushDomain(domain: SyncDomain) {
        val payload = runCatching { snapshot(domain) }
            .onFailure { if (it is CancellationException) throw it }
            .getOrNull() ?: return

        mutex.withLock {
            runCatching { api.putNoContent("/me/sync/${domain.wireKey}", SyncPushRequestDto(payload)) }
                .onFailure {
                    if (it is CancellationException) throw it
                    Napier.d(tag = "Sync", message = "push failed for ${domain.wireKey}: ${it.message}")
                }
        }
    }

    override suspend fun pullAll() {
        val result = mutex.withLock {
            runCatching { api.getResult<SyncPullResponseDto>("/me/sync") }
                .onFailure { if (it is CancellationException) throw it }
                .getOrNull()
                .also { restoreAll(it) }
        }
        if (result is DataResult.Failure) {
            Napier.d(tag = "Sync", message = "pullAll failed: ${result.message}")
        }
    }

    private suspend fun restoreAll(result: DataResult<SyncPullResponseDto>?) {
        val domains = (result as? DataResult.Success)?.data?.domains ?: return
        domains["profile"]?.let { restoreProfile(it) }
        domains["workoutProgress"]?.let { workoutProgressStore.restoreRaw(it.payload) }
        domains["sleep"]?.let { sleepStore.restoreRaw(it.payload) }
        domains["weight"]?.let { weightStore.restoreRaw(it.payload) }
    }

    private suspend fun snapshot(domain: SyncDomain): String? = when (domain) {
        SyncDomain.PROFILE -> profileRepository.getProfile()?.let { json.encodeToString(Profile.serializer(), it) }
        SyncDomain.WORKOUT_PROGRESS -> workoutProgressStore.rawSnapshot()
        SyncDomain.SLEEP -> sleepStore.rawSnapshot()
        SyncDomain.WEIGHT -> weightStore.rawSnapshot()
    }

    private suspend fun restoreProfile(blob: SyncBlobDto) {
        runCatching { json.decodeFromString(Profile.serializer(), blob.payload) }
            .onSuccess { profileRepository.restoreProfile(it) }
            .onFailure {
                if (it is CancellationException) throw it
                Napier.d(tag = "Sync", message = "profile restore failed: ${it.message}")
            }
    }
}
