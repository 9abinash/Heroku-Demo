package com.kidsshorts.data.repo

import com.kidsshorts.data.db.DecisionDao
import com.kidsshorts.data.db.VideoDecisionEntity
import com.kidsshorts.domain.LanguageCatalog
import com.kidsshorts.domain.LanguageNormalizer
import com.kidsshorts.domain.ParentDecision
import com.kidsshorts.domain.VideoIds

/** Manual per-video decisions. Every decision is keyed by the exact video ID, never by channel. */
class ReviewRepository(
    private val dao: DecisionDao,
    private val audit: AuditRepository,
    private val clock: () -> Long,
) {
    /** Approve one video and record the language the parent has confirmed for it. */
    suspend fun approve(videoId: String, confirmedLanguageCode: String?, title: String) {
        if (!VideoIds.isValid(videoId)) return
        val code = LanguageNormalizer.normalize(confirmedLanguageCode)
        dao.upsert(VideoDecisionEntity(videoId, ParentDecision.APPROVED.name, code, clock()))
        audit.log(AuditType.VIDEO_APPROVED, "$title [$videoId] language=${LanguageCatalog.label(code)}")
    }

    suspend fun block(videoId: String, title: String) {
        if (!VideoIds.isValid(videoId)) return
        dao.upsert(VideoDecisionEntity(videoId, ParentDecision.BLOCKED.name, null, clock()))
        audit.log(AuditType.VIDEO_BLOCKED, "$title [$videoId]")
    }

    /** Undo: removes the decision so the video is judged by metadata and policy again. */
    suspend fun clearDecision(videoId: String, title: String) {
        dao.delete(videoId)
        audit.log(AuditType.VIDEO_DECISION_UNDONE, "$title [$videoId]")
    }
}
