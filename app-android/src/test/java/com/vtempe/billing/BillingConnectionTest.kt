package com.vtempe.billing

import kotlinx.coroutines.async
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** VT-AUD-055: Play Billing's reusable listener must not resume a finished wait. */
class BillingConnectionTest {

    private class FakeSdk {
        var ready = false
        var starts = 0
        val listeners = mutableListOf<(Boolean) -> Unit>()
        val connection = BillingConnection(isReady = { ready }) { onResult -> starts++; listeners += onResult }
    }

    @Test
    fun aLateDisconnectAfterSetupIsHarmless() = runTest {
        val sdk = FakeSdk()
        val first = async { sdk.connection.connect() }
        runCurrent()
        sdk.listeners.single()(true) // setup finished
        sdk.ready = true
        assertTrue(first.await())

        sdk.ready = false
        sdk.listeners.single()(false) // the SDK reuses the listener on disconnect: must not throw
    }

    @Test
    fun concurrentCallersShareOneAttemptAndAReconnectStartsANewOne() = runTest {
        val sdk = FakeSdk()
        val a = async { sdk.connection.connect() }
        val b = async { sdk.connection.connect() }
        runCurrent()
        assertEquals(1, sdk.starts)
        sdk.listeners.single()(false) // setup failed
        sdk.listeners.single()(true) // a second report for the same attempt is ignored
        assertFalse(a.await())
        assertFalse(b.await())

        val retry = async { sdk.connection.connect() }
        runCurrent()
        assertEquals(2, sdk.starts)
        sdk.listeners.last()(true)
        assertTrue(retry.await())
    }

    @Test
    fun aReadyClientDoesNotReconnect() = runTest {
        val sdk = FakeSdk().apply { ready = true }
        assertTrue(sdk.connection.connect())
        assertEquals(0, sdk.starts)
    }
}
