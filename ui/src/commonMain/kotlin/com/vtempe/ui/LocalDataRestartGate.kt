package com.vtempe.ui

/**
 * Decides when device data was cleared or replaced (sign-out, account switch, reset, deletion)
 * so the whole app is rebuilt and no screen keeps the previous owner's data (VT-AUD-072).
 * Watched at the app root, so it fires whatever screen is open — not only while Auth is shown.
 */
internal class LocalDataRestartGate(private var seen: Int?) {
    /** True once for every new [generation] after the first one seen. */
    fun changed(generation: Int?): Boolean {
        if (generation == null || generation == seen) return false
        val first = seen == null
        seen = generation
        return !first
    }
}
