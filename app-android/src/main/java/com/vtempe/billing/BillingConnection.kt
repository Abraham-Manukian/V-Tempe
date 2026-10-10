package com.vtempe.billing

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Turns Play Billing's long-lived connection listener into "connect and tell me if it worked"
 * (VT-AUD-055). The SDK keeps the listener after setup and calls it again on every disconnect;
 * each attempt is a [CompletableDeferred], whose `complete` ignores every call after the first,
 * so a late callback can no longer resume an already finished wait and crash the app. Callers
 * that ask while an attempt is running share it.
 */
internal class BillingConnection(
    private val isReady: () -> Boolean,
    /** Starts one connection attempt; the SDK reports through `onResult` (true = connected). */
    private val startConnection: (onResult: (Boolean) -> Unit) -> Unit,
) {
    private val lock = Mutex()
    private var attempt: CompletableDeferred<Boolean>? = null

    suspend fun connect(): Boolean {
        if (isReady()) return true
        val current = lock.withLock {
            attempt?.takeIf { it.isActive } ?: CompletableDeferred<Boolean>().also { next ->
                attempt = next
                startConnection { connected -> next.complete(connected) }
            }
        }
        return current.await()
    }
}
