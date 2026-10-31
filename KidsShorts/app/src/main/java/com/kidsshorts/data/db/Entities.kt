package com.kidsshorts.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "channels")
data class ChannelEntity(
    @PrimaryKey val channelId: String,
    val title: String,
    val thumbnailUrl: String?,
    val uploadsPlaylistId: String?,
    val isEnabled: Boolean,
    val addedAt: Long,
    val lastSyncedAt: Long,
)

@Entity(tableName = "videos", indices = [Index("channelId")])
data class VideoEntity(
    @PrimaryKey val videoId: String,
    val channelId: String,
    val title: String,
    val thumbnailUrl: String?,
    val durationSeconds: Int,
    val isEmbeddable: Boolean,
    val isPubliclyAvailable: Boolean,
    val madeForKids: Boolean?,
    val metadataLanguage: String?,
    val audioLanguage: String?,
    val playerReportedUnavailable: Boolean,
    val publishedAt: Long,
    val fetchedAt: Long,
)

/** A parent's decision about ONE exact video. Never keyed by channel. */
@Entity(tableName = "video_decisions")
data class VideoDecisionEntity(
    @PrimaryKey val videoId: String,
    val decision: String, // ParentDecision.name
    val confirmedLanguageCode: String?,
    val updatedAt: Long,
)

/** Single-row table (id = 1). Language list is a comma-separated set of normalised codes. */
@Entity(tableName = "settings")
data class SettingsEntity(
    @PrimaryKey val id: Int = 1,
    val allowedLanguages: String,
    val preferredLanguage: String?,
    val blockUnknownLanguage: Boolean,
    val dailyLimitMinutes: Int,
    val quietEnabled: Boolean,
    val quietStartMinute: Int,
    val quietEndMinute: Int,
)

@Entity(tableName = "viewing_sessions", indices = [Index("dateKey")])
data class ViewingSessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val dateKey: String, // local date yyyy-MM-dd at session start
    val startedAt: Long,
    val endedAt: Long,
    val durationSeconds: Int,
)

@Entity(tableName = "screen_time_overrides")
data class OverrideEntity(
    @PrimaryKey val dateKey: String,
    val extraSeconds: Int,
    val quietBypassUntil: Long,
)

@Entity(tableName = "audit_log")
data class AuditEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: Long,
    val type: String,
    val detail: String,
)
