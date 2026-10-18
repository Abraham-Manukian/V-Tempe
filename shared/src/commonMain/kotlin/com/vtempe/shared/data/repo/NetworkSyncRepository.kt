package com.vtempe.shared.data.repo

import com.vtempe.shared.data.network.ApiClient
import com.vtempe.shared.data.network.dto.SyncChangesDto
import com.vtempe.shared.data.network.dto.SyncPullResponseDto
import com.vtempe.shared.data.network.dto.SyncPushRequestDto
import com.vtempe.shared.domain.account.AccountDataFailure
import com.vtempe.shared.domain.account.LocalDataOwnerStore
import com.vtempe.shared.domain.consent.HealthDataConsentGate
import com.vtempe.shared.domain.model.Profile
import com.vtempe.shared.domain.repository.AuthRepository
import com.vtempe.shared.domain.repository.RemoteFetch
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
    private val syncBase: SyncBaseStore,
    /** The server backup is one of the purposes the health-data consent covers (and the server is
     *  abroad): without it nothing with the user's data leaves the device. Changes stay pending
     *  and are delivered once consent is given; empty payloads (erasing the server copy) still go. */
    private val consent: HealthDataConsentGate,
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

    override suspend fun deliverAll(uid: String): Boolean = mutex.withLock {
        // Without consent nothing can be sent; the device is "delivered" exactly when nothing waits.
        if (!consent.isHealthDataConsentGranted()) return@withLock pending.pendingDomains().isEmpty()
        val token = auth.idTokenFor(uid) ?: return@withLock false
        SyncDomain.entries.all { uploadTracked(it, token) }
    }

    /**
     * Delivers [domain] and marks it delivered at the revision read before its data (VT-AUD-056).
     * A diary sends only what changed since the last synced state (VT-AUD-057); the profile is
     * sent whole. True when delivered or there was nothing to deliver.
     */
    private suspend fun uploadTracked(domain: SyncDomain, token: String): Boolean {
        val revision = pending.currentRevision(domain)
        val payload = when (val local = readSnapshot(domain)) {
            is LocalSnapshot.Present -> local.payload
            // An emptied diary removes its entries; no profile means nothing to send.
            LocalSnapshot.Absent -> if (domain.diary) RemoteSyncSnapshot.EMPTY_PAYLOAD else return true
            LocalSnapshot.Unreadable -> return false // VT-AUD-058: never sent as "nothing here"
        }
        if (!mayUpload(payload)) return false // stays pending until consent is given
        val delivered = if (domain.diary) deliverDiary(domain, payload, token) else upload(domain, payload, token)
        if (delivered) pending.confirm(domain, revision)
        return delivered
    }

    private suspend fun deliverDiary(domain: SyncDomain, payload: String, token: String): Boolean {
        if (!mayUpload(payload)) return false // consent withdrawn while other domains were sent
        val changes = diaryChanges(syncBase.load(domain), payload) ?: return upload(domain, payload, token)
        if (changes.isEmpty) return true
        val result = api.patchNoContent("/me/sync/${domain.wireKey}", changes, bearerToken = token)
        return when {
            result is DataResult.Success -> true.also { syncBase.save(domain, payload) }
            // A server from before diary merging: the whole snapshot, as before.
            result is DataResult.Failure && result.code in LEGACY_SERVER_CODES -> upload(domain, payload, token)
            else -> false.also {
                val failure = result as DataResult.Failure
                Napier.d(tag = "Sync", message = "merge failed for ${domain.wireKey}: ${failure.reason} ${failure.code ?: ""}")
            }
        }
    }

    override suspend fun pushAll(uid: String): Boolean = mutex.withLock {
        val token = auth.idTokenFor(uid) ?: return@withLock false
        // Read everything first: the account is replaced as a whole or not at all, so a domain the
        // consent holds back never leaves the others half-replaced on the server.
        val snapshots = SyncDomain.entries.map { domain ->
            val revision = pending.currentRevision(domain)
            val payload = when (val local = readSnapshot(domain)) {
                is LocalSnapshot.Present -> local.payload
                LocalSnapshot.Absent -> RemoteSyncSnapshot.EMPTY_PAYLOAD
                // VT-AUD-058: a failed read is not "nothing here" — uploading "{}" would erase the
                // account's good copy and let sign-out wipe the device as if it were backed up.
                LocalSnapshot.Unreadable -> return@withLock false
            }
            Triple(domain, revision, payload)
        }
        if (!snapshots.all { (_, _, payload) -> mayUpload(payload) }) return@withLock false
        snapshots.all { (domain, revision, payload) ->
            upload(domain, payload, token).also { delivered -> if (delivered) pending.confirm(domain, revision) }
        }
    }

    override fun hasUndeliveredChanges(): Boolean = pending.pendingDomains().isNotEmpty()

    override suspend fun awaitIdle() {
        mutex.withLock { }
    }

    override suspend fun fetchRemote(uid: String): RemoteFetch {
        val token = auth.idTokenFor(uid) ?: return RemoteFetch.Failed(
            // Still signed in as [uid] but no token: it could not be refreshed, usually offline.
            if (auth.authState.value?.uid == uid) AccountDataFailure.OFFLINE else AccountDataFailure.SESSION_EXPIRED
        )
        return when (val result = api.getResult<SyncPullResponseDto>("/me/sync", bearerToken = token)) {
            is DataResult.Success -> RemoteFetch.Fetched(RemoteSyncSnapshot(result.data.domains.mapValues { it.value.payload }))
            is DataResult.Failure -> {
                Napier.d(tag = "Sync", message = "fetch failed: ${result.reason} ${result.code ?: ""}")
                RemoteFetch.Failed(result.toAccountDataFailure())
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
            val payload = snapshot.payloads[domain.wireKey]
            val restored = payload == null || restoreDomain(domain, payload)
            // The device now holds the account's diary: later uploads send changes against it.
            if (restored && domain.diary) syncBase.save(domain, payload ?: RemoteSyncSnapshot.EMPTY_PAYLOAD)
            !restored
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

    /** Only an empty payload (no user data) may go to the server without the health-data consent. */
    private fun mayUpload(payload: String): Boolean =
        payload == RemoteSyncSnapshot.EMPTY_PAYLOAD || consent.isHealthDataConsentGranted()

    private suspend fun upload(domain: SyncDomain, payload: String, token: String): Boolean {
        if (!mayUpload(payload)) return false // re-checked per request: a withdrawal stops the rest
        val result = api.putNoContent("/me/sync/${domain.wireKey}", SyncPushRequestDto(payload), bearerToken = token)
        if (result is DataResult.Failure) {
            Napier.d(tag = "Sync", message = "push failed for ${domain.wireKey}: ${result.reason} ${result.code ?: ""}")
        }
        if (result is DataResult.Success && domain.diary) syncBase.save(domain, payload)
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

    private companion object {
        /** No PATCH route yet: Not Found or Method Not Allowed. */
        val LEGACY_SERVER_CODES = setOf(404, 405)
    }

    private fun decodeProfile(payload: String): Profile? =
        runCatching { json.decodeFromString(Profile.serializer(), payload) }
            .onFailure { Napier.d(tag = "Sync", message = "profile snapshot unreadable: ${it::class.simpleName}") }
            .getOrNull()
}
