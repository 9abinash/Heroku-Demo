package com.kidsshorts.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ChannelDao {
    @Query("SELECT * FROM channels ORDER BY title COLLATE NOCASE")
    fun observeAll(): Flow<List<ChannelEntity>>

    @Query("SELECT * FROM channels ORDER BY title COLLATE NOCASE")
    suspend fun getAll(): List<ChannelEntity>

    @Query("SELECT * FROM channels WHERE channelId = :id")
    suspend fun get(id: String): ChannelEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(channel: ChannelEntity)

    @Query("UPDATE channels SET isEnabled = :enabled WHERE channelId = :id")
    suspend fun setEnabled(id: String, enabled: Boolean)

    @Query("UPDATE channels SET lastSyncedAt = :at WHERE channelId = :id")
    suspend fun setSynced(id: String, at: Long)

    @Query("DELETE FROM channels WHERE channelId = :id")
    suspend fun delete(id: String)
}

@Dao
interface VideoDao {
    @Query("SELECT * FROM videos")
    fun observeAll(): Flow<List<VideoEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(videos: List<VideoEntity>)

    @Query("SELECT videoId FROM videos WHERE channelId = :channelId AND fetchedAt < :olderThan")
    suspend fun staleIds(channelId: String, olderThan: Long): List<String>

    @Query("UPDATE videos SET isPubliclyAvailable = 0 WHERE videoId IN (:ids)")
    suspend fun markUnavailable(ids: List<String>)

    @Query("UPDATE videos SET playerReportedUnavailable = 1 WHERE videoId = :id")
    suspend fun markPlayerUnavailable(id: String)

    @Query("UPDATE videos SET playerReportedUnavailable = 0 WHERE channelId = :channelId")
    suspend fun clearPlayerUnavailable(channelId: String)

    @Query("DELETE FROM video_decisions WHERE videoId IN (SELECT videoId FROM videos WHERE channelId = :channelId)")
    suspend fun deleteDecisionsForChannel(channelId: String)

    @Query("DELETE FROM videos WHERE channelId = :channelId")
    suspend fun deleteByChannel(channelId: String)
}

@Dao
interface DecisionDao {
    @Query("SELECT * FROM video_decisions")
    fun observeAll(): Flow<List<VideoDecisionEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(decision: VideoDecisionEntity)

    @Query("DELETE FROM video_decisions WHERE videoId = :videoId")
    suspend fun delete(videoId: String)
}

@Dao
interface SettingsDao {
    @Query("SELECT * FROM settings WHERE id = 1")
    fun observe(): Flow<SettingsEntity?>

    @Query("SELECT * FROM settings WHERE id = 1")
    suspend fun get(): SettingsEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(settings: SettingsEntity)
}

@Dao
interface SessionDao {
    @Insert
    suspend fun insert(session: ViewingSessionEntity): Long

    @Query("UPDATE viewing_sessions SET endedAt = :endedAt, durationSeconds = :durationSeconds WHERE id = :id")
    suspend fun update(id: Long, endedAt: Long, durationSeconds: Int)

    @Query("SELECT COALESCE(SUM(durationSeconds), 0) FROM viewing_sessions WHERE dateKey = :dateKey")
    fun observeSecondsFor(dateKey: String): Flow<Int>
}

@Dao
interface OverrideDao {
    @Query("SELECT * FROM screen_time_overrides WHERE dateKey = :dateKey")
    fun observe(dateKey: String): Flow<OverrideEntity?>

    @Query("SELECT * FROM screen_time_overrides WHERE dateKey = :dateKey")
    suspend fun get(dateKey: String): OverrideEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: OverrideEntity)
}

@Dao
interface AuditDao {
    @Insert
    suspend fun insert(entry: AuditEntity)

    @Query("SELECT * FROM audit_log ORDER BY timestamp DESC, id DESC LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<AuditEntity>>
}
