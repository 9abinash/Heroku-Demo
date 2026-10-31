package com.kidsshorts.data.db

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [
        ChannelEntity::class, VideoEntity::class, VideoDecisionEntity::class, SettingsEntity::class,
        ViewingSessionEntity::class, OverrideEntity::class, AuditEntity::class,
    ],
    version = 1,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun channelDao(): ChannelDao
    abstract fun videoDao(): VideoDao
    abstract fun decisionDao(): DecisionDao
    abstract fun settingsDao(): SettingsDao
    abstract fun sessionDao(): SessionDao
    abstract fun overrideDao(): OverrideDao
    abstract fun auditDao(): AuditDao
}
