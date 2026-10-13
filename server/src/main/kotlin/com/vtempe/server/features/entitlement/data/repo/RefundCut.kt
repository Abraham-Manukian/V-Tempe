package com.vtempe.server.features.entitlement.data.repo

import com.vtempe.server.features.entitlement.domain.model.GrantWindow
import java.time.Duration
import java.time.Instant

/**
 * The refund policy as arithmetic, shared by both repositories (VT-AUD-052): a fully refunded
 * payment loses the part of its window after [now] — the part already used can't be taken back —
 * and every later window of the same user moves up by that much, so no other payment is lost.
 */
internal class RefundCut private constructor(private val window: GrantWindow, private val now: Instant) {

    /** How much access the refund takes away; zero once its window is over. */
    val removed: Duration = Duration.between(maxOf(window.from, now), window.until).let { if (it.isNegative) Duration.ZERO else it }

    /** The refunded payment's window, ended where its unused part began. */
    val refundedWindow: GrantWindow = GrantWindow(window.from, window.until.minus(removed))

    /** Later windows start where the refunded one ended (or after it). */
    fun movesUp(other: GrantWindow): Boolean = !removed.isZero && !other.from.isBefore(window.until)

    fun movedUp(other: GrantWindow): GrantWindow = GrantWindow(other.from.minus(removed), other.until.minus(removed))

    companion object {
        fun of(window: GrantWindow, now: Instant) = RefundCut(window, now)
    }
}
