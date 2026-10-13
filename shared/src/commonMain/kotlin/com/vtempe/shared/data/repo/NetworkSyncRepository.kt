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
    private val weightStore: WeightStore,
    private val pending: PendingSyncStore,
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
            uploadTracked(domain, token)
        }
    }

    override suspend fun pushPending() {
        mutex.withLock {
            val uid = owner.ownerUid() ?: return
            val token = auth.idTokenFor(uid) ?: return
            pending.pendingDomains().forEach { uploadTracked(it, token) }
        }
    }

    /** Uploads [domain] and marks it delivered at the revision read before its data (VT-AUD-056). */
    private suspend fun uploadTracked(domain: SyncDomain, token: String) {
        val revision = pending.currentRevision(domain)
        val payload = (readSnapshot(domain) as? LocalSnapshot.Present)?.payload ?: return
        if (upload(domain, payload, token)) pending.confirm(domain, revision)
    }

    override suspend fun pushAll(uid: String): Boolean = mutex.withLock {
        val token = auth.idTokenFor(uid) ?: return@withLock false
        SyncDomain.entries.all { domain ->
            val revision = pending.currentRevision(domain)
            val payload = when (val local = readSnapshot(domain)) {
                is LocalSnapshot.Present -> local.payload
                LocalSnapshot.Absent -> RemoteSyncSnapshot.EMPTY_PAYLOAD
                // VT-AUD-058: a failed read is not "nothing here" — uploading "{}" would erase the
                // account's good copy and let sign-out wipe the device as if it were backed up.
                LocalSnapshot.Unreadable -> return@withLock false
            }
            upload(domain, payload, token).also { delivered -> if (delivered) pending.confirm(domain, revision) }
        }
    }

    override suspend fun awaitIdle() {
        mutex.withLock { }
    }

    override suspend fun fetchRemote(uid: String): RemoteSyncSnapshot? {
        val token = auth.idTokenFor(uid) ?: return null
        return when (val result = api.getResult<SyncPullResponseDto>("/me/sync", bearerToken = token)) {
            is DataResult.Success -> RemoteSyncSnapshot(result.data.domains.mapValues { it.value.payload })
            is DataResult.Failure -> {
                Napier.d(tag = "Sync", message = "fetch failed: ${result.reason} ${result.code ?: ""}")
                null
            }
        }
    }

    override fun unreadableDomains(snapshot: RemoteSyncSnapshot): Set<SyncDomain> =
        SyncDomain.entries.filterTo(mutableSetOf()) { domain ->
            val payload = snapshot.payloads[domain.wireKey] ?: return@filterTo false
            !isReadable(domain, payload)
        }

    override suspend fun restore(snapshot: RemoteSyncSnapshot): Set<SyncDomain> = mutex.withLock {
        // All or nothing (VT-AUD-060): every payload is read first, so one unreadable domain never
        // leaves a half-restored account behind.
        val unreadable = unreadableDomains(snapshot)
        if (unreadable.isNotEmpty()) {
            Napier.w(tag = "Sync", message = "unreadable snapshot, nothing restored: ${unreadable.joinToString { it.wireKey }}")
            return@withLock unreadable
        }
        SyncDomain.entries.filterTo(mutableSetOf()) { domain ->
            val payload = snapshot.payloads[domain.wireKey] ?: return@filterTo false
            !restoreDomain(domain, payload)
        }
    }

    private fun isReadable(domain: SyncDomain, payload: String): Boolean = when (domain) {
        SyncDomain.PROFILE -> payload == RemoteSyncSnapshot.EMPTY_PAYLOAD || decodeProfile(payload) != null
        SyncDomain.WORKOUT_PROGRESS -> workoutProgressStore.isReadableSnapshot(payload)
        SyncDomain.SLEEP -> sleepStore.isReadableSnapshot(payload)
        SyncDomain.SLEEP_NOTES -> sleepStore.isReadableNotesSnapshot(payload)
        SyncDomain.WEIGHT -> weightStore.isReadableSnapshot(payload)
    }

    /** False when [payload] could not be read; local data for [domain] is then left as it was. */
    private suspend fun restoreDomain(domain: SyncDomain, payload: String): Boolean = when (domain) {
        SyncDomain.PROFILE -> restoreProfile(payload)
        SyncDomain.WORKOUT_PROGRESS -> workoutProgressStore.restoreRaw(payload)
        SyncDomain.SLEEP -> sleepStore.restoreRaw(payload)
        SyncDomain.SLEEP_NOTES -> sleepStore.restoreNotesRaw(payload)
        SyncDomain.WEIGHT -> weightStore.restoreRaw(payload)
    }

    private suspend fun upload(domain: SyncDomain, payload: String, token: String): Boolean {
        val result = api.putNoContent("/me/sync/${domain.wireKey}", SyncPushRequestDto(payload), bearerToken = token)
        if (result is DataResult.Failure) {
            Napier.d(tag = "Sync", message = "push failed for ${domain.wireKey}: ${result.reason} ${result.code ?: ""}")
        }
        return result is DataResult.Success
    }

    /** What the device holds for one domain: data, deliberately nothing, or a read that failed. */
    private sealed interface LocalSnapshot {
        class Present(val payload: String) : LocalSnapshot
        data object Absent : LocalSnapshot
        data object Unreadable : LocalSnapshot
    }

    private suspend fun readSnapshot(domain: SyncDomain): LocalSnapshot =
        runCatching { snapshot(domain) }
            .fold(
                onSuccess = { payload -> payload?.let(LocalSnapshot::Present) ?: LocalSnapshot.Absent },
                onFailure = {
                    if (it is CancellationException) throw it
                    Napier.w(tag = "Sync", message = "local ${domain.wireKey} unreadable: ${it::class.simpleName}")
                    LocalSnapshot.Unreadable
                },
            )

    private suspend fun snapshot(domain: SyncDomain): String? = when (domain) {
        SyncDomain.PROFILE -> profileRepository.getProfile()?.let { json.encodeToString(Profile.serializer(), it) }
        SyncDomain.WORKOUT_PROGRESS -> workoutProgressStore.rawSnapshot()
        SyncDomain.SLEEP -> sleepStore.rawSnapshot()
        SyncDomain.SLEEP_NOTES -> sleepStore.rawNotesSnapshot()
        SyncDomain.WEIGHT -> weightStore.rawSnapshot()
    }

    private suspend fun restoreProfile(payload: String): Boolean {
        if (payload == RemoteSyncSnapshot.EMPTY_PAYLOAD) return true // the account deliberately has no profile
        val profile = decodeProfile(payload) ?: return false
        profileRepository.restoreProfile(profile) // a write failure propagates
        return true
    }

    private fun decodeProfile(payload: String): Profile? =
        runCatching { json.decodeFromString(Profile.serializer(), payload) }
            .onFailure { Napier.d(tag = "Sync", message = "profile snapshot unreadable: ${it::class.simpleName}") }
            .getOrNull()
}
