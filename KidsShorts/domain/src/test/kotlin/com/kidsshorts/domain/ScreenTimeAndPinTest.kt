package com.kidsshorts.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScreenTimePolicyTest {
    private val noQuiet = ScreenTimeSettings(30, QuietHours(false, 20 * 60, 7 * 60))
    private val quiet = ScreenTimeSettings(30, QuietHours(true, 20 * 60, 7 * 60))
    private fun min(h: Int, m: Int = 0) = h * 60 + m

    @Test fun underLimit_isOpen_withRemainingSeconds() {
        val g = ScreenTimePolicy.evaluate(noQuiet, 600, ScreenTimeOverride.NONE, min(12), 0)
        assertEquals(PlaybackGate.Open(1200), g)
    }

    @Test fun atOrOverLimit_isLimitReached() {
        assertEquals(PlaybackGate.LimitReached, ScreenTimePolicy.evaluate(noQuiet, 1800, ScreenTimeOverride.NONE, min(12), 0))
        assertEquals(PlaybackGate.LimitReached, ScreenTimePolicy.evaluate(noQuiet, 9999, ScreenTimeOverride.NONE, min(12), 0))
    }

    @Test fun parentExtraTime_extendsTodayBudget() {
        val g = ScreenTimePolicy.evaluate(noQuiet, 1800, ScreenTimeOverride(600, 0), min(12), 0)
        assertEquals(PlaybackGate.Open(600), g)
    }

    @Test fun quietHours_wrapMidnight() {
        val q = QuietHours(true, 20 * 60, 7 * 60)
        assertTrue(q.contains(min(23)))
        assertTrue(q.contains(min(3)))
        assertTrue(q.contains(min(20)))
        assertFalse(q.contains(min(7)))
        assertFalse(q.contains(min(12)))
    }

    @Test fun quietHours_sameDayWindow() {
        val q = QuietHours(true, 13 * 60, 15 * 60)
        assertTrue(q.contains(min(13, 30)))
        assertFalse(q.contains(min(15)))
        assertFalse(q.contains(min(12, 59)))
    }

    @Test fun quietHours_equalStartAndEnd_isNotAllDay() {
        assertFalse(QuietHours(true, 600, 600).contains(600))
    }

    @Test fun quietHours_disabled_neverBlocks() {
        assertFalse(QuietHours(false, 0, 1439).contains(100))
    }

    @Test fun quietHours_blockEvenWithTimeRemaining() {
        val g = ScreenTimePolicy.evaluate(quiet, 0, ScreenTimeOverride.NONE, min(22), 0)
        assertEquals(PlaybackGate.QuietHoursActive(7 * 60), g)
    }

    @Test fun quietHoursBypass_isTimeBoxed() {
        val ov = ScreenTimeOverride(0, quietHoursBypassUntilEpochMs = 1_000)
        assertTrue(ScreenTimePolicy.evaluate(quiet, 0, ov, min(22), nowEpochMs = 999) is PlaybackGate.Open)
        assertTrue(ScreenTimePolicy.evaluate(quiet, 0, ov, min(22), nowEpochMs = 1_000) is PlaybackGate.QuietHoursActive)
    }

    @Test fun negativeInputs_cannotCreateExtraTime() {
        val g = ScreenTimePolicy.evaluate(ScreenTimeSettings(-5, QuietHours.DEFAULT), -100, ScreenTimeOverride(-50, 0), min(12), 0)
        assertEquals(PlaybackGate.LimitReached, g)
    }
}

class PinThrottleTest {
    private var state = PinAttemptState.FRESH
    private fun fail(at: Long) { state = PinThrottle.onFailure(state, at) }

    @Test fun firstFourFailures_doNotLock() {
        repeat(4) { fail(1_000L + it) }
        assertFalse(PinThrottle.isLocked(state, 2_000))
        assertEquals(4, state.consecutiveFailures)
    }

    @Test fun fifthFailure_locksFor30Seconds() {
        repeat(5) { fail(10_000) }
        assertEquals(30_000, PinThrottle.remainingLockMs(state, 10_000))
        assertFalse(PinThrottle.isLocked(state, 40_000))
    }

    @Test fun lockoutEscalates_andIsCappedAtOneHour() {
        repeat(5) { fail(0) }; assertEquals(30_000, state.lockedUntilEpochMs)
        fail(100_000); assertEquals(100_000 + 60_000, state.lockedUntilEpochMs)
        fail(200_000); assertEquals(200_000 + 300_000, state.lockedUntilEpochMs)
        fail(1_000_000); assertEquals(1_000_000 + 900_000, state.lockedUntilEpochMs)
        fail(3_000_000); assertEquals(3_000_000 + 3_600_000, state.lockedUntilEpochMs)
        fail(8_000_000); assertEquals(8_000_000 + 3_600_000, state.lockedUntilEpochMs)
    }

    @Test fun settingClockBackwards_doesNotShortenLock() {
        repeat(5) { fail(1_000_000) }
        val lockedUntil = state.lockedUntilEpochMs
        assertTrue(PinThrottle.isLocked(state, nowEpochMs = 0))        // clock rolled back to 1970
        assertEquals(lockedUntil - 1_000_000, PinThrottle.remainingLockMs(state, 0))
    }

    @Test fun success_resetsCounter_butRemembersLatestTime() {
        repeat(3) { fail(5_000) }
        state = PinThrottle.onSuccess(9_000, state)
        assertEquals(0, state.consecutiveFailures)
        assertEquals(9_000, state.lastSeenEpochMs)
        assertFalse(PinThrottle.isLocked(state, 9_000))
    }
}
