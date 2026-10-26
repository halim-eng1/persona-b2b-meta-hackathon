package com.example.persona.features.auto_reply.domain.orchestrator

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for the pure merge-once decision behind the dedup verification
 * pivot ([AutoReplyOrchestrator.isPivotStillOpen]).
 *
 * Contract under test:
 *  - A pivot present in the recent window is OPEN until one extraction merge
 *    consumes it (by the pivot message's timestamp).
 *  - Once consumed by that timestamp, the same pivot stays CLOSED — a later
 *    extraction must not silently append into the old order (finding: a
 *    genuinely new order was being merged into the previous order's notes).
 *  - A NEW pivot (newer timestamp) reopens the cycle.
 *  - No pivot message at all is never "open".
 */
class PivotSuppressionPolicyTest {

    @Test
    fun `pivot that was never answered is open`() {
        assertTrue(AutoReplyOrchestrator.isPivotStillOpen(latestPivotTimestamp = 1000L, consumedPivotTimestamps = emptySet()))
    }

    @Test
    fun `pivot consumed by its own timestamp stays closed`() {
        assertFalse(AutoReplyOrchestrator.isPivotStillOpen(latestPivotTimestamp = 1000L, consumedPivotTimestamps = setOf(1000L)))
    }

    @Test
    fun `a newer pivot reopens the merge cycle`() {
        assertTrue(AutoReplyOrchestrator.isPivotStillOpen(latestPivotTimestamp = 2000L, consumedPivotTimestamps = setOf(1000L)))
    }

    @Test
    fun `an older pivot consumed earlier in the session stays closed`() {
        // Window regression: the latest visible pivot predates a pivot that was
        // already answered — per-instance consumption must keep it closed.
        assertFalse(AutoReplyOrchestrator.isPivotStillOpen(latestPivotTimestamp = 500L, consumedPivotTimestamps = setOf(500L, 1000L)))
    }

    @Test
    fun `no pivot message is never open`() {
        assertFalse(AutoReplyOrchestrator.isPivotStillOpen(latestPivotTimestamp = null, consumedPivotTimestamps = emptySet()))
        assertFalse(AutoReplyOrchestrator.isPivotStillOpen(latestPivotTimestamp = null, consumedPivotTimestamps = setOf(1000L)))
    }
}
