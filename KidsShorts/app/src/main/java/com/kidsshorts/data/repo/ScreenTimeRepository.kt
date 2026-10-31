package com.kidsshorts.data.repo

import com.kidsshorts.data.db.OverrideDao
import com.kidsshorts.data.db.OverrideEntity
import com.kidsshorts.data.db.SessionDao
import com.kidsshorts.data.db.ViewingSessionEntity
import com.kidsshorts.domain.PlaybackGate
import com.kidsshorts.domain.ScreenTimeOverride
import com.kidsshorts.domain.ScreenTimePolicy
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Instant
import java.time.ZoneId

/**
 * Daily-usage accounting that survives restarts: usage lives in the database, is flushed every few
 * seconds while the child feed is on screen, and is measured with a MONOTONIC clock so changing the
 * device clock cannot refill the budget (changing the date can still reach a new "day" - see docs).
 */
class ScreenTimeRepository(
    private val sessionDao: SessionDao,
    private val overrideDao: OverrideDao,
    private val settings: SettingsRepository,
    private val audit: AuditRepository,
    private val clock: () -> Long = System::currentTimeMillis,
    private val monotonicMs: () -> Long = { System.nanoTime() / 1_000_000 },
    private val zone: () -> ZoneId = { ZoneId.systemDefault() },
) {
    private fun zoned(now: Long) = Instant.ofEpochMilli(now).atZone(zone())
    private fun dateKey(now: Long): String = zoned(now).toLocalDate().toString()
    private fun minuteOfDay(now: Long): Int = zoned(now).let { it.hour * 60 + it.minute }

    private val ticker: Flow<Long> = flow {
        while (true) {
            emit(clock())
            delay(TICK_MS)
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    val gate: Flow<PlaybackGate> = ticker.map { dateKey(it) }.distinctUntilChanged().flatMapLatest { key ->
        combine(sessionDao.observeSecondsFor(key), overrideDao.observe(key), settings.screenTime, ticker) { used, ov, cfg, now ->
            ScreenTimePolicy.evaluate(
                cfg, used, ov?.toDomain() ?: ScreenTimeOverride.NONE, minuteOfDay(now), now,
            )
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    val usedTodaySeconds: Flow<Int> = ticker.map { dateKey(it) }.distinctUntilChanged().flatMapLatest { sessionDao.observeSecondsFor(it) }

    suspend fun grantExtraTime(extraMinutes: Int, quietBypassMinutes: Int = 0) {
        val now = clock()
        val key = dateKey(now)
        val existing = overrideDao.get(key)
        val extra = (existing?.extraSeconds ?: 0) + extraMinutes.coerceAtLeast(0) * 60
        val bypass = if (quietBypassMinutes > 0) maxOf(existing?.quietBypassUntil ?: 0L, now + quietBypassMinutes * 60_000L)
        else existing?.quietBypassUntil ?: 0L
        overrideDao.upsert(OverrideEntity(key, extra, bypass))
        audit.log(
            AuditType.SCREEN_TIME_OVERRIDE,
            buildString {
                append("Parent override for $key:")
                if (extraMinutes > 0) append(" +$extraMinutes min")
                if (quietBypassMinutes > 0) append(" quiet hours bypassed for $quietBypassMinutes min")
            },
        )
    }

    fun newTracker() = SessionTracker()

    /** One open "viewing session" row, updated in place so a crash loses at most one tick. */
    inner class SessionTracker {
        private val mutex = Mutex()
        private var id: Long? = null
        private var startedWall = 0L
        private var startedMono = 0L
        private var key = ""

        suspend fun start() = mutex.withLock { open() }

        /** Flush elapsed time; rolls into a new session row when the local date changes. */
        suspend fun tick() = mutex.withLock {
            val rowId = id ?: return@withLock
            val now = clock()
            if (dateKey(now) != key) {
                flush(rowId, now)
                open()
            } else {
                flush(rowId, now)
            }
        }

        suspend fun stop() = mutex.withLock {
            id?.let { flush(it, clock()) }
            id = null
        }

        private suspend fun open() {
            val now = clock()
            startedWall = now
            startedMono = monotonicMs()
            key = dateKey(now)
            id = sessionDao.insert(ViewingSessionEntity(dateKey = key, startedAt = now, endedAt = now, durationSeconds = 0))
        }

        private suspend fun flush(rowId: Long, now: Long) {
            val seconds = ((monotonicMs() - startedMono) / 1000).toInt().coerceAtLeast(0)
            sessionDao.update(rowId, now, seconds)
        }
    }

    private fun OverrideEntity.toDomain() = ScreenTimeOverride(extraSeconds, quietBypassUntil)

    private companion object { const val TICK_MS = 5_000L }
}
