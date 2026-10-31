package com.kidsshorts.domain

data class QuietHours(
    val enabled: Boolean,
    /** Minutes after local midnight, 0..1439. */
    val startMinuteOfDay: Int,
    val endMinuteOfDay: Int,
) {
    /** start == end is treated as "not configured" rather than "all day" so a typo can't lock the app. */
    fun contains(minuteOfDay: Int): Boolean {
        if (!enabled || startMinuteOfDay == endMinuteOfDay) return false
        return if (startMinuteOfDay < endMinuteOfDay) {
            minuteOfDay in startMinuteOfDay until endMinuteOfDay
        } else {
            // Wraps midnight, e.g. 20:00 -> 07:00
            minuteOfDay >= startMinuteOfDay || minuteOfDay < endMinuteOfDay
        }
    }

    companion object {
        val DEFAULT = QuietHours(enabled = false, startMinuteOfDay = 20 * 60, endMinuteOfDay = 7 * 60)
    }
}

data class ScreenTimeSettings(
    val dailyLimitMinutes: Int,
    val quietHours: QuietHours,
) {
    companion object {
        val DEFAULT = ScreenTimeSettings(30, QuietHours.DEFAULT)
        val PRESET_LIMITS = listOf(15, 30, 45, 60)
    }
}

/** What a parent override granted for *today only*. Persisted so a restart cannot erase it. */
data class ScreenTimeOverride(
    val extraSeconds: Int,
    val quietHoursBypassUntilEpochMs: Long,
) {
    companion object {
        val NONE = ScreenTimeOverride(0, 0L)
    }
}

sealed interface PlaybackGate {
    data class Open(val remainingSeconds: Int) : PlaybackGate
    data object LimitReached : PlaybackGate
    data class QuietHoursActive(val endsAtMinuteOfDay: Int) : PlaybackGate
}

object ScreenTimePolicy {
    /**
     * Pure function of persisted state. Usage is accumulated playback/foreground time stored in the
     * database (not a wall-clock delta), so restarting the app or changing the clock cannot refill it.
     */
    fun evaluate(
        settings: ScreenTimeSettings,
        usedSecondsToday: Int,
        override: ScreenTimeOverride,
        minuteOfDay: Int,
        nowEpochMs: Long,
    ): PlaybackGate {
        val bypassed = nowEpochMs < override.quietHoursBypassUntilEpochMs
        if (!bypassed && settings.quietHours.contains(minuteOfDay)) {
            return PlaybackGate.QuietHoursActive(settings.quietHours.endMinuteOfDay)
        }
        val budget = settings.dailyLimitMinutes.coerceAtLeast(0) * 60 + override.extraSeconds.coerceAtLeast(0)
        val remaining = budget - usedSecondsToday.coerceAtLeast(0)
        return if (remaining > 0) PlaybackGate.Open(remaining) else PlaybackGate.LimitReached
    }
}
