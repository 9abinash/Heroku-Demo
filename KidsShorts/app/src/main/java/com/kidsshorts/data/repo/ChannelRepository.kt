package com.kidsshorts.data.repo

import android.util.Log
import com.kidsshorts.data.db.ChannelDao
import com.kidsshorts.data.db.ChannelEntity
import com.kidsshorts.data.db.VideoDao
import com.kidsshorts.data.db.toEntity
import com.kidsshorts.data.youtube.ChannelInfo
import com.kidsshorts.data.youtube.YouTubeApiClient
import com.kidsshorts.data.youtube.YouTubeApiException
import com.kidsshorts.domain.ChannelInput
import com.kidsshorts.domain.ChannelInputParser
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

sealed interface LookupResult {
    data class Found(val info: ChannelInfo, val alreadyApproved: Boolean) : LookupResult
    data class Rejected(val message: String) : LookupResult
    data class Failed(val message: String) : LookupResult
}

sealed interface SyncOutcome {
    data class Done(val videosFound: Int) : SyncOutcome
    data class Failed(val message: String, val quotaExceeded: Boolean = false) : SyncOutcome
}

fun YouTubeApiException.userMessage(): String = when (kind) {
    YouTubeApiException.Kind.API_KEY_MISSING -> "No YouTube API key is set. Add one under Data & privacy."
    YouTubeApiException.Kind.KEY_REJECTED -> "YouTube rejected the API key. Check the key and its restrictions."
    YouTubeApiException.Kind.QUOTA_EXCEEDED -> "The daily YouTube API quota is used up. Try again tomorrow."
    YouTubeApiException.Kind.NOT_FOUND -> message ?: "Not found on YouTube."
    YouTubeApiException.Kind.NETWORK -> message ?: "Couldn't reach YouTube."
    YouTubeApiException.Kind.HTTP, YouTubeApiException.Kind.BAD_RESPONSE -> message ?: "YouTube returned an error."
}

class ChannelRepository(
    private val api: YouTubeApiClient,
    private val channelDao: ChannelDao,
    private val videoDao: VideoDao,
    private val audit: AuditRepository,
    private val clock: () -> Long,
) {
    private val syncMutex = Mutex()

    /** Step 1 of approval: resolve and PREVIEW a channel. Nothing is approved or stored yet. */
    suspend fun lookup(raw: String): LookupResult {
        val parsed = ChannelInputParser.parse(raw)
        if (parsed is ChannelInput.Rejected) return LookupResult.Rejected(parsed.reason.message)
        return try {
            val info = api.resolveChannel(parsed)
            LookupResult.Found(info, alreadyApproved = channelDao.get(info.channelId) != null)
        } catch (e: YouTubeApiException) {
            LookupResult.Failed(e.userMessage())
        }
    }

    /** Step 2: the parent explicitly approves the previewed channel. */
    suspend fun approve(info: ChannelInfo): SyncOutcome {
        val now = clock()
        channelDao.upsert(
            ChannelEntity(
                channelId = info.channelId, title = info.title, thumbnailUrl = info.thumbnailUrl,
                uploadsPlaylistId = info.uploadsPlaylistId, isEnabled = true, addedAt = now, lastSyncedAt = 0L,
            ),
        )
        audit.log(AuditType.CHANNEL_APPROVED, "${info.title} (${info.channelId})")
        return sync(info.channelId)
    }

    suspend fun sync(channelId: String): SyncOutcome = syncMutex.withLock {
        val channel = channelDao.get(channelId) ?: return@withLock SyncOutcome.Failed("Channel not found.")
        val playlist = channel.uploadsPlaylistId ?: return@withLock SyncOutcome.Failed("This channel has no public uploads list.")
        val now = clock()
        try {
            val newest = api.listUploadVideoIds(playlist)
            // Re-validate older cached videos too: availability and Made-for-Kids status can change.
            val stale = videoDao.staleIds(channelId, now - STALE_AFTER_MS)
            val wanted = (newest + stale).distinct()
            val fetched = api.getVideos(wanted).filter { it.channelId == channelId }
            val returned = fetched.map { it.videoId }.toSet()
            val missing = wanted.filter { it !in returned } // deleted/private/not attributable => fail closed

            videoDao.clearPlayerUnavailable(channelId) // give player-reported failures one fresh retry
            videoDao.upsertAll(fetched.map { it.toEntity(now) })
            if (missing.isNotEmpty()) videoDao.markUnavailable(missing)
            channelDao.setSynced(channelId, now)
            SyncOutcome.Done(fetched.size)
        } catch (e: YouTubeApiException) {
            Log.w(TAG, "Sync failed for $channelId: ${e.kind}")
            SyncOutcome.Failed(e.userMessage(), e.kind == YouTubeApiException.Kind.QUOTA_EXCEEDED)
        }
    }

    /** Opportunistic refresh of enabled channels not synced recently. Errors are logged, not shown. */
    suspend fun syncStale(maxAgeMs: Long = REFRESH_AFTER_MS) {
        val now = clock()
        for (c in channelDao.getAll()) {
            if (!c.isEnabled || now - c.lastSyncedAt < maxAgeMs) continue
            val outcome = sync(c.channelId)
            if (outcome is SyncOutcome.Failed && outcome.quotaExceeded) return // stop burning quota
        }
    }

    suspend fun setEnabled(channelId: String, enabled: Boolean) {
        channelDao.setEnabled(channelId, enabled)
        val title = channelDao.get(channelId)?.title ?: channelId
        audit.log(if (enabled) AuditType.CHANNEL_RESUMED else AuditType.CHANNEL_PAUSED, title)
    }

    suspend fun remove(channelId: String) {
        val title = channelDao.get(channelId)?.title ?: channelId
        videoDao.deleteDecisionsForChannel(channelId)
        videoDao.deleteByChannel(channelId)
        channelDao.delete(channelId)
        audit.log(AuditType.CHANNEL_REMOVED, "$title ($channelId)")
    }

    private companion object {
        const val TAG = "ChannelRepository"
        const val STALE_AFTER_MS = 7L * 24 * 60 * 60 * 1000
        const val REFRESH_AFTER_MS = 12L * 60 * 60 * 1000
    }
}
