package com.kidsshorts.data.repo

import android.util.Log
import com.kidsshorts.data.db.ChannelDao
import com.kidsshorts.data.db.DecisionDao
import com.kidsshorts.data.db.VideoDao
import com.kidsshorts.data.db.toDomain
import com.kidsshorts.data.db.toDomainOrNull
import com.kidsshorts.domain.ApprovedChannel
import com.kidsshorts.domain.EligibilityEvaluator
import com.kidsshorts.domain.EligibilityResult
import com.kidsshorts.domain.ExclusionReason
import com.kidsshorts.domain.FeedOrdering
import com.kidsshorts.domain.LanguagePolicy
import com.kidsshorts.domain.PlaybackRules
import com.kidsshorts.domain.VideoDecision
import com.kidsshorts.domain.VideoItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map

data class VideoEvaluation(val video: VideoItem, val result: EligibilityResult)

data class FeedSnapshot(
    val channels: List<ApprovedChannel>,
    val policy: LanguagePolicy,
    val decisions: Map<String, VideoDecision>,
    val evaluations: List<VideoEvaluation>,
) {
    val eligible: List<EligibilityResult.Eligible>
        get() = evaluations.mapNotNull { it.result as? EligibilityResult.Eligible }
}

/** An evaluator-approved video plus the channel title to display. Only obtainable via [FeedRepository]. */
class ChildVideo(val eligible: EligibilityResult.Eligible, val channelTitle: String)

/** Needs a parent's eyes: the ONLY thing stopping it is missing/conflicting language information. */
fun VideoEvaluation.needsReview(): Boolean {
    val excluded = result as? EligibilityResult.Excluded ?: return false
    return excluded.reasons.all { it == ExclusionReason.LANGUAGE_UNKNOWN || it == ExclusionReason.LANGUAGE_CONFLICT }
}

/** Logs *why* a video is excluded, but only when its reason set changes (no log spam). */
class EligibilityLogger {
    private val last = HashMap<String, List<ExclusionReason>>()

    fun record(evaluations: List<VideoEvaluation>) {
        for (e in evaluations) {
            val reasons = (e.result as? EligibilityResult.Excluded)?.reasons ?: emptyList()
            val previous = last.put(e.video.videoId, reasons)
            if (previous != reasons && reasons.isNotEmpty()) {
                Log.d(TAG, "EXCLUDED ${e.video.videoId} channel=${e.video.channelId} reasons=$reasons")
            }
        }
    }

    private companion object { const val TAG = "Eligibility" }
}

/**
 * The ONLY source of feed contents. Every emission re-runs [EligibilityEvaluator] over current
 * channels, videos, decisions and policy, so a parent change (disable a channel, remove a language,
 * block a video) takes effect on the very next emission.
 */
class FeedRepository(
    channelDao: ChannelDao,
    private val videoDao: VideoDao,
    decisionDao: DecisionDao,
    settings: SettingsRepository,
    private val logger: EligibilityLogger,
    private val rules: PlaybackRules = PlaybackRules(),
) {
    val snapshot: Flow<FeedSnapshot> = combine(
        channelDao.observeAll(), videoDao.observeAll(), decisionDao.observeAll(), settings.languagePolicy,
    ) { channelEntities, videoEntities, decisionEntities, policy ->
        val channels = channelEntities.map { it.toDomain() }
        val channelMap = channels.associateBy { it.channelId }
        val decisions = decisionEntities.mapNotNull { it.toDomainOrNull() }.associateBy { it.videoId }
        val evaluations = videoEntities.map { it.toDomain() }.map { video ->
            VideoEvaluation(video, EligibilityEvaluator.evaluateVideo(video, channelMap, policy, decisions, rules))
        }
        logger.record(evaluations)
        FeedSnapshot(channels, policy, decisions, evaluations)
    }.flowOn(Dispatchers.Default)

    /** Ordered list the child can swipe through. Contains ONLY evaluator-approved videos. */
    val childFeed: Flow<List<ChildVideo>> = snapshot.map { snap ->
        val titles = snap.channels.associate { it.channelId to it.title }
        FeedOrdering.order(snap.eligible, snap.policy.preferredLanguageCode)
            .map { ChildVideo(it, titles[it.video.channelId].orEmpty()) }
    }

    /** The official player said this video can't be played here (removed, private, embedding off, region). */
    suspend fun markPlayerUnavailable(videoId: String) = videoDao.markPlayerUnavailable(videoId)
}
