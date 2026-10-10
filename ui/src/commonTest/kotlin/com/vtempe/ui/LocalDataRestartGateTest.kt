package com.vtempe.ui

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** VT-AUD-072: every change of device data rebuilds the app once, wherever the user is. */
class LocalDataRestartGateTest {
    @Test
    fun eachNewGenerationRestartsOnce() {
        val gate = LocalDataRestartGate(seen = 0)
        assertFalse(gate.changed(0), "nothing changed")
        assertTrue(gate.changed(1), "sign-out finished while another screen was open")
        assertFalse(gate.changed(1), "the same change does not restart twice")
        assertTrue(gate.changed(2))
    }

    @Test
    fun theFirstKnownGenerationIsNotAChange() {
        val gate = LocalDataRestartGate(seen = null) // the session was not available at first composition
        assertFalse(gate.changed(3))
        assertTrue(gate.changed(4))
        assertFalse(gate.changed(null))
    }
}
