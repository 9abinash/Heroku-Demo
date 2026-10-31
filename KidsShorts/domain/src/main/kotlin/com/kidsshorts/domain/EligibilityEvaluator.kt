package com.kidsshorts.domain

/**
 * THE single place that decides whether a child may see a video.
 * Deterministic, side-effect free, and fail-closed: a video is eligible only if the list of
 * exclusion reasons is empty. UI code consumes [EligibilityResult.Eligible] only and cannot build one.
 */
object EligibilityEvaluator {

    fun evaluateVideo(
        video: VideoItem,
        approvedChannels: Map<String, ApprovedChannel>,
        languagePolicy: LanguagePolicy,
        parentVideoDecisions: Map<String, VideoDecision>,
        rules: PlaybackRules = PlaybackRules(),
    ): EligibilityResult {
        val decision = parentVideoDecisions[video.videoId]
        val language = LanguageResolver.resolve(video, decision)
        val reasons = ArrayList<ExclusionReason>(2)

        // 1. Channel must be explicitly approved AND currently enabled.
        val channel = approvedChannels[video.channelId]
        if (channel == null) reasons += ExclusionReason.CHANNEL_NOT_APPROVED
        else if (!channel.isEnabled) reasons += ExclusionReason.CHANNEL_DISABLED

        // 2. Must be available and playable through the official embedded player.
        if (!video.isPubliclyAvailable || video.playerReportedUnavailable) reasons += ExclusionReason.VIDEO_UNAVAILABLE
        if (!video.isEmbeddable) reasons += ExclusionReason.VIDEO_NOT_EMBEDDABLE
        // Policy: Made-for-Kids status must be known before embedding.
        if (video.madeForKids == null) reasons += ExclusionReason.STATUS_NOT_VERIFIED

        // Shorts format is best-effort (duration only); see docs.
        if (video.durationSeconds !in 1..rules.maxShortDurationSeconds) reasons += ExclusionReason.NOT_SHORTS_FORMAT

        // 3. Parent block on this exact video.
        if (decision?.decision == ParentDecision.BLOCKED) reasons += ExclusionReason.VIDEO_BLOCKED_BY_PARENT

        // 4 + 5. Language: known (verified or parent-confirmed) AND exact match with allowlist.
        val allowed = languagePolicy.allowedLanguageCodes.mapNotNull(LanguageNormalizer::normalize).toSet()
        if (allowed.isEmpty()) {
            // Empty allowlist means nothing is playable, even if "allow unknown" is switched on.
            reasons += ExclusionReason.LANGUAGE_NOT_ALLOWED
        } else {
            when (language.status) {
                LanguageStatus.CONFLICTING -> reasons += ExclusionReason.LANGUAGE_CONFLICT
                LanguageStatus.UNKNOWN ->
                    if (languagePolicy.blockUnknownLanguage) reasons += ExclusionReason.LANGUAGE_UNKNOWN
                LanguageStatus.VERIFIED_BY_METADATA, LanguageStatus.PARENT_CONFIRMED ->
                    if (language.languageCode !in allowed) reasons += ExclusionReason.LANGUAGE_NOT_ALLOWED
            }
        }

        return if (reasons.isEmpty()) EligibilityResult.Eligible(video, language)
        else EligibilityResult.Excluded(video.videoId, reasons.toList(), language)
    }

    fun evaluateAll(
        videos: List<VideoItem>,
        approvedChannels: Map<String, ApprovedChannel>,
        languagePolicy: LanguagePolicy,
        parentVideoDecisions: Map<String, VideoDecision>,
        rules: PlaybackRules = PlaybackRules(),
    ): List<EligibilityResult> =
        videos.map { evaluateVideo(it, approvedChannels, languagePolicy, parentVideoDecisions, rules) }
}

/**
 * Orders ALREADY-ELIGIBLE videos. The preferred language only changes ordering; it can never
 * add a video, widen the allowlist, or act as a fallback.
 */
object FeedOrdering {
    fun order(eligible: List<EligibilityResult.Eligible>, preferredLanguageCode: String?): List<EligibilityResult.Eligible> {
        val preferred = LanguageNormalizer.normalize(preferredLanguageCode)
        return eligible.sortedWith(
            compareByDescending<EligibilityResult.Eligible> { preferred != null && it.language.languageCode == preferred }
                .thenByDescending { it.video.publishedAtEpochMs }
                .thenBy { it.video.videoId },
        )
    }
}
