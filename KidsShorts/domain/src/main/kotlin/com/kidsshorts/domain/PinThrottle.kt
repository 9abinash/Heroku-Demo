package com.kidsshorts.domain

/** Persisted (encrypted) brute-force counter for the parent PIN and recovery code. */
data class PinAttemptState(
    val consecutiveFailures: Int,
    val lockedUntilEpochMs: Long,
    /** Highest wall-clock time we have ever observed; defeats "set the clock back" shortcuts. */
    val lastSeenEpochMs: Long,
) {
    companion object {
        val FRESH = PinAttemptState(0, 0L, 0L)
    }
}

object PinThrottle {
    private const val FREE_ATTEMPTS = 4
    private val lockoutSecondsByFailure = longArrayOf(30, 60, 5 * 60, 15 * 60, 60 * 60) // 5th..9th+

    /** Time that has to be trusted: never earlier than anything we've already seen. */
    fun effectiveNow(state: PinAttemptState, nowEpochMs: Long): Long = maxOf(nowEpochMs, state.lastSeenEpochMs)

    fun remainingLockMs(state: PinAttemptState, nowEpochMs: Long): Long =
        (state.lockedUntilEpochMs - effectiveNow(state, nowEpochMs)).coerceAtLeast(0L)

    fun isLocked(state: PinAttemptState, nowEpochMs: Long): Boolean = remainingLockMs(state, nowEpochMs) > 0

    fun onFailure(state: PinAttemptState, nowEpochMs: Long): PinAttemptState {
        val now = effectiveNow(state, nowEpochMs)
        val failures = state.consecutiveFailures + 1
        val lockSeconds = if (failures <= FREE_ATTEMPTS) 0L
        else lockoutSecondsByFailure[(failures - FREE_ATTEMPTS - 1).coerceAtMost(lockoutSecondsByFailure.lastIndex)]
        return PinAttemptState(failures, if (lockSeconds > 0) now + lockSeconds * 1000 else state.lockedUntilEpochMs, now)
    }

    fun onSuccess(nowEpochMs: Long, previous: PinAttemptState): PinAttemptState =
        PinAttemptState.FRESH.copy(lastSeenEpochMs = effectiveNow(previous, nowEpochMs))
}
