package com.vtempe.shared.data.repo

import com.vtempe.shared.domain.repository.SyncDomain
import com.vtempe.shared.domain.repository.SyncRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * What every store calls after a local write: the change is first recorded as owed to the server
 * (on disk, synchronously — VT-AUD-056), then an upload starts in the background. If that upload
 * fails or the app dies first, [SyncRepository.pushPending] delivers it later.
 *
 * [sync] is resolved lazily: SyncRepository depends on the stores that call this.
 */
class LocalChangeNotifier(
    private val pending: PendingSyncStore,
    private val appScope: CoroutineScope,
    private val sync: () -> SyncRepository,
) {
    fun onChanged(domain: SyncDomain) {
        pending.markChanged(domain)
        appScope.launch { sync().pushDomain(domain) }
    }
}
