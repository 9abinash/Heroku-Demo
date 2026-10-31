package com.kidsshorts.data.repo

import com.kidsshorts.data.db.AuditDao
import com.kidsshorts.data.db.AuditEntity
import kotlinx.coroutines.flow.Flow

object AuditType {
    const val CHANNEL_APPROVED = "CHANNEL_APPROVED"
    const val CHANNEL_REMOVED = "CHANNEL_REMOVED"
    const val CHANNEL_PAUSED = "CHANNEL_PAUSED"
    const val CHANNEL_RESUMED = "CHANNEL_RESUMED"
    const val LANGUAGES_CHANGED = "LANGUAGES_CHANGED"
    const val LANGUAGE_POLICY_CHANGED = "LANGUAGE_POLICY_CHANGED"
    const val VIDEO_APPROVED = "VIDEO_APPROVED"
    const val VIDEO_BLOCKED = "VIDEO_BLOCKED"
    const val VIDEO_DECISION_UNDONE = "VIDEO_DECISION_UNDONE"
    const val SCREEN_TIME_SETTINGS = "SCREEN_TIME_SETTINGS"
    const val SCREEN_TIME_OVERRIDE = "SCREEN_TIME_OVERRIDE"
    const val PIN_CHANGED = "PIN_CHANGED"
    const val PIN_RESET_VIA_RECOVERY = "PIN_RESET_VIA_RECOVERY"
    const val API_KEY_CHANGED = "API_KEY_CHANGED"
}

/** Parent-facing history of every control change and override. Contains no child personal data. */
class AuditRepository(private val dao: AuditDao, private val clock: () -> Long) {
    suspend fun log(type: String, detail: String) {
        dao.insert(AuditEntity(timestamp = clock(), type = type, detail = detail))
    }

    fun recent(limit: Int = 100): Flow<List<AuditEntity>> = dao.observeRecent(limit)
}
