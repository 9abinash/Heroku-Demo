package com.kidsshorts.domain

/** How confident we are about a video's spoken language. Metadata is evidence, never proof. */
enum class LanguageStatus {
    /** Backed by YouTube's defaultAudioLanguage (and not contradicted by defaultLanguage). */
    VERIFIED_BY_METADATA,

    /** A parent explicitly approved this exact video and labelled its language. */
    PARENT_CONFIRMED,

    /** No reliable signal. Blocked by default. */
    UNKNOWN,

    /** Signals disagree. Always blocked until a parent reviews the video. */
    CONFLICTING,
}

enum class ParentDecision { APPROVED, BLOCKED }

/** Why a video was excluded from the child feed. A video can have several reasons at once. */
enum class ExclusionReason {
    CHANNEL_NOT_APPROVED,
    CHANNEL_DISABLED,
    VIDEO_UNAVAILABLE,
    VIDEO_NOT_EMBEDDABLE,
    STATUS_NOT_VERIFIED,
    NOT_SHORTS_FORMAT,
    VIDEO_BLOCKED_BY_PARENT,
    LANGUAGE_NOT_ALLOWED,
    LANGUAGE_UNKNOWN,
    LANGUAGE_CONFLICT,
}

data class ApprovedChannel(
    val channelId: String,
    val title: String,
    val thumbnailUrl: String?,
    val uploadsPlaylistId: String?,
    val isEnabled: Boolean,
)

/**
 * Only fields that the official YouTube Data API v3 can legitimately provide with an API key.
 * Caption languages are deliberately absent: captions.list requires OAuth 2.0 authorisation.
 * Language *status* is not stored here: it is always derived (see [LanguageResolver]) so it
 * can never go stale when the parent changes a decision.
 */
data class VideoItem(
    val videoId: String,
    val channelId: String,
    val title: String,
    val thumbnailUrl: String?,
    val durationSeconds: Int,
    val isEmbeddable: Boolean,
    /** status.privacyStatus == "public" and status.uploadStatus == "processed". */
    val isPubliclyAvailable: Boolean,
    /** status.madeForKids. null means we have not retrieved it, so the video must not be embedded. */
    val madeForKids: Boolean?,
    /** snippet.defaultLanguage, as returned. Language of title/description, not of the audio. */
    val metadataLanguage: String?,
    /** snippet.defaultAudioLanguage, as returned. */
    val audioLanguage: String?,
    /** Set when the official player itself reported this video cannot be played here. */
    val playerReportedUnavailable: Boolean,
    val publishedAtEpochMs: Long,
)

data class LanguagePolicy(
    val allowedLanguageCodes: Set<String>,
    val preferredLanguageCode: String?,
    val blockUnknownLanguage: Boolean = true,
) {
    companion object {
        /** Strictest possible default: nothing allowed until a parent chooses languages. */
        val STRICT_DEFAULT = LanguagePolicy(emptySet(), null, true)
    }
}

/** A parent's decision about one exact video ID. Never applies to other videos or the channel. */
data class VideoDecision(
    val videoId: String,
    val decision: ParentDecision,
    val confirmedLanguageCode: String?,
    val updatedAtEpochMs: Long,
)

data class LanguageResolution(val status: LanguageStatus, val languageCode: String?)

/** Tunable, non-language playback rules. */
data class PlaybackRules(
    /** The Data API has no Shorts flag. Shorts can be up to 3 minutes since 15 Oct 2024. */
    val maxShortDurationSeconds: Int = 180,
)

sealed interface EligibilityResult {
    /**
     * Constructor is internal to :domain, so no UI/data code can fabricate an "eligible" result.
     * The only way to obtain one is [EligibilityEvaluator.evaluateVideo].
     */
    class Eligible internal constructor(
        val video: VideoItem,
        val language: LanguageResolution,
    ) : EligibilityResult

    data class Excluded(
        val videoId: String,
        val reasons: List<ExclusionReason>,
        val language: LanguageResolution,
    ) : EligibilityResult {
        val primaryReason: ExclusionReason get() = reasons.first()
    }
}
