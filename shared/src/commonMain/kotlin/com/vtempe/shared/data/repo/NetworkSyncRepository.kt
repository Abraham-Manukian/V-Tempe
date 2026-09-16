package com.vtempe.shared.data.repo

import com.vtempe.shared.data.network.ApiClient
import com.vtempe.shared.data.network.dto.SyncPullResponseDto
import com.vtempe.shared.data.network.dto.SyncPushRequestDto
import com.vtempe.shared.domain.account.LocalDataOwnerStore
import com.vtempe.shared.domain.model.Profile
import com.vtempe.shared.domain.repository.AuthRepository
import com.vtempe.shared.domain.repository.RemoteSyncSnapshot
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
    private val auth: AuthRepository,
    private val owner: LocalDataOwnerStore,
    // Concrete type, not the ProfileRepository interface — restoring a pulled snapshot needs
    // restoreProfile() (skips the onLocalChange hook), which isn't part of that interface.
    private val profileRepository: ProfileRepositoryDb,
    private val workoutProgressStore: WorkoutProgressStore,
    private val sleepStore: SleepStore,
    private val weightStore: WeightStore
) : SyncRepository {

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    // Serializes every push/restore against every other one. restoreRaw()/restoreProfile() have
    // no version check, so a restore landing after a concurrent push would silently discard that
    // fresh local write. Coarse-grained is fine: sync runs a few times a day, never in a hot path.
    private val mutex = Mutex()

    override suspend fun pushDomain(domain: SyncDomain) {
        mutex.withLock {
            // Only the owning account may receive device data. The token is bound to that uid, so a
            // sign-in switch mid-request can't redirect the upload to another account.
            val uid = owner.ownerUid() ?: return
            val token = auth.idTokenFor(uid) ?: return
            val payload = snapshotOrNull(domain) ?: return
            upload(domain, payload, token)
        }
    }

    override suspend fun pushAll(uid: String): Boolean = mutex.withLock {
        val token = auth.idTokenFor(uid) ?: return@withLock false
        SyncDomain.entries.all { domain ->
            upload(domain, snapshotOrNull(domain) ?: RemoteSyncSnapshot.EMPTY_PAYLOAD, token)
        }
    }

    override suspend fun fetchRemote(uid: String): RemoteSyncSnapshot? {
        val token = auth.idTokenFor(uid) ?: return null
        return when (val result = api.getResult<SyncPullResponseDto>("/me/sync", bearerToken = token)) {
            is DataResult.Success -> RemoteSyncSnapshot(result.data.domains.mapValues { it.value.payload })
            is DataResult.Failure -> {
                Napier.d(tag = "Sync", message = "fetch failed: ${result.message}")
                null
            }
        }
    }

    override suspend fun restore(snapshot: RemoteSyncSnapshot) {
        mutex.withLock {
            val payloads = snapshot.payloads
            payloads[SyncDomain.PROFILE.wireKey]?.let { restoreProfile(it) }
            payloads[SyncDomain.WORKOUT_PROGRESS.wireKey]?.let { workoutProgressStore.restoreRaw(it) }
            payloads[SyncDomain.SLEEP.wireKey]?.let { sleepStore.restoreRaw(it) }
            payloads[SyncDomain.SLEEP_NOTES.wireKey]?.let { sleepStore.restoreNotesRaw(it) }
            payloads[SyncDomain.WEIGHT.wireKey]?.let { weightStore.restoreRaw(it) }
        }
    }

    private suspend fun upload(domain: SyncDomain, payload: String, token: String): Boolean {
        val ok = api.putNoContent("/me/sync/${domain.wireKey}", SyncPushRequestDto(payload), bearerToken = token)
        if (!ok) Napier.d(tag = "Sync", message = "push failed for ${domain.wireKey}")
        return ok
    }

    private suspend fun snapshotOrNull(domain: SyncDomain): String? =
        runCatching { snapshot(domain) }
            .onFailure { if (it is CancellationException) throw it }
            .getOrNull()

    private suspend fun snapshot(domain: SyncDomain): String? = when (domain) {
        SyncDomain.PROFILE -> profileRepository.getProfile()?.let { json.encodeToString(Profile.serializer(), it) }
        SyncDomain.WORKOUT_PROGRESS -> workoutProgressStore.rawSnapshot()
        SyncDomain.SLEEP -> sleepStore.rawSnapshot()
        SyncDomain.SLEEP_NOTES -> sleepStore.rawNotesSnapshot()
        SyncDomain.WEIGHT -> weightStore.rawSnapshot()
    }

    private suspend fun restoreProfile(payload: String) {
        if (payload == RemoteSyncSnapshot.EMPTY_PAYLOAD) return // the account deliberately has no profile
        runCatching { json.decodeFromString(Profile.serializer(), payload) }
            .onSuccess { profileRepository.restoreProfile(it) }
            .onFailure {
                if (it is CancellationException) throw it
                Napier.d(tag = "Sync", message = "profile restore failed: ${it.message}")
            }
    }
}
