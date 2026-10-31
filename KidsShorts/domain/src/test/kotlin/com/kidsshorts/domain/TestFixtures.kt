package com.kidsshorts.domain

internal object Fx {
    fun channel(id: String = "UC_approved", enabled: Boolean = true) =
        ApprovedChannel(id, "Channel $id", null, "UU_$id", enabled)

    fun channels(vararg c: ApprovedChannel = arrayOf(channel())) = c.associateBy { it.channelId }

    fun video(
        id: String = "v1",
        channelId: String = "UC_approved",
        audio: String? = "hi",
        meta: String? = null,
        duration: Int = 30,
        embeddable: Boolean = true,
        available: Boolean = true,
        madeForKids: Boolean? = false,
        playerUnavailable: Boolean = false,
        published: Long = 1_000L,
    ) = VideoItem(
        videoId = id, channelId = channelId, title = "t", thumbnailUrl = null,
        durationSeconds = duration, isEmbeddable = embeddable, isPubliclyAvailable = available,
        madeForKids = madeForKids, metadataLanguage = meta, audioLanguage = audio,
        playerReportedUnavailable = playerUnavailable, publishedAtEpochMs = published,
    )

    fun policy(vararg langs: String = arrayOf("hi"), preferred: String? = null, blockUnknown: Boolean = true) =
        LanguagePolicy(langs.toSet(), preferred, blockUnknown)

    fun decisions(vararg d: VideoDecision) = d.associateBy { it.videoId }

    fun approve(id: String = "v1", lang: String? = null) = VideoDecision(id, ParentDecision.APPROVED, lang, 1L)
    fun block(id: String = "v1") = VideoDecision(id, ParentDecision.BLOCKED, null, 1L)

    fun eval(
        video: VideoItem = video(),
        channels: Map<String, ApprovedChannel> = channels(),
        policy: LanguagePolicy = policy(),
        decisions: Map<String, VideoDecision> = emptyMap(),
    ) = EligibilityEvaluator.evaluateVideo(video, channels, policy, decisions)
}

internal fun EligibilityResult.isEligible() = this is EligibilityResult.Eligible
internal fun EligibilityResult.reasons(): List<ExclusionReason> =
    (this as? EligibilityResult.Excluded)?.reasons ?: emptyList()
